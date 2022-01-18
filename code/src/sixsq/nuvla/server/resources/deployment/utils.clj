(ns sixsq.nuvla.server.resources.deployment.utils
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-api-key]
    [sixsq.nuvla.server.resources.deployment-log :as deployment-log]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.job.interface :as job-interface]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as time]))


(defn generate-api-key-secret
  [deployment-id authn-info]
  (let [template {:name        (str "API credential for " deployment-id)
                  :description (str "generated API credential for " deployment-id)
                  :parent      deployment-id
                  :template    {:href (str "credential-template/" cred-api-key/method)}}
        {{:keys [status
                 resource-id
                 secret-key]} :body} (credential/create-credential
                                       template
                                       (or authn-info
                                           {:user-id      deployment-id
                                            :active-claim deployment-id
                                            :claims       #{deployment-id
                                                            "group/nuvla-user"
                                                            "group/nuvla-anon"}}))]
    (if (= status 201)
      {:api-key    resource-id
       :api-secret secret-key}
      (throw (r/ex-response (format "exception when creating api key/secret for %s"
                                    deployment-id) 500 deployment-id)))))


(defn delete-child-resources
  "Attempts to delete (as admin) all child resources associated with the
   deployment via the parent attribute. The type of resource is provided as a
   parameter. Exceptions are logged but otherwise ignored."
  [resource-name deployment-id]
  (try
    (let [query     {:params      {:resource-name resource-name}
                     :cimi-params {:filter (cimi-params-impl/cimi-filter {:filter (str "parent='" deployment-id "'")})
                                   :select ["id"]}
                     :nuvla/authn auth/internal-identity}
          child-ids (->> query crud/query :body :resources (map :id))]

      (doseq [child-id child-ids]
        (try
          (let [[resource-name uuid] (u/parse-id child-id)
                request {:params      {:resource-name resource-name
                                       :uuid          uuid}
                         :nuvla/authn auth/internal-identity}]
            (crud/delete request))
          (catch Exception e
            (log/error (str "error deleting " (:id child-id) " for " deployment-id ": " e))))))
    (catch Exception _
      (log/errorf "cannot query %s resources related to %s" resource-name deployment-id))))


(defn delete-all-child-resources
  "Attempts to delete (as admin) all credential, deployment-parameter, and
   deployment-log resources associated with the deployment. Exceptions are
   logged but otherwise ignored."
  [deployment-id]
  (doseq [resource-name #{credential/resource-type "deployment-parameter"
                          deployment-log/resource-type}]
    (delete-child-resources resource-name deployment-id)))


(defn propagate-acl-to-dep-parameters
  [deployment-id acl]
  (try
    (let [query     {:params      {:resource-name "deployment-parameter"}
                     :cimi-params {:filter (cimi-params-impl/cimi-filter
                                             {:filter (str "parent='" deployment-id "'")})
                                   :select ["id"]}
                     :nuvla/authn auth/internal-identity}
          child-ids (->> query crud/query :body :resources (map :id))]

      (doseq [child-id child-ids]
        (try
          (let [[resource-name uuid] (u/parse-id child-id)
                request {:params      {:resource-name resource-name
                                       :uuid          uuid}
                         :body        {:acl acl}
                         :nuvla/authn auth/internal-identity}]
            (crud/edit request))
          (catch Exception e
            (log/errorf "error propagating acl to %s for %s: %s" (:id child-id) deployment-id e)))))
    (catch Exception _
      (log/errorf "cannot propagate acl to deployment parameters related to %s" deployment-id))))


(defn resolve-module [request]
  (let [authn-info     (auth/current-authentication request)
        href           (get-in request [:body :module :href])
        params         (u/id->request-params href)
        module-request {:params params, :nuvla/authn authn-info}
        response       (crud/retrieve module-request)
        module-body    (:body response)]
    (if (= (:status response) 200)
      (-> module-body
          (dissoc :versions :operations)                    ;; dissoc versions to avoid resolving all hrefs
          (std-crud/resolve-hrefs authn-info true)
          (assoc :versions (:versions module-body))
          (assoc :href href))
      (throw (r/ex-bad-request (str "cannot resolve " href))))))


(defn resolve-deployment [request]
  (let [authn-info         (auth/current-authentication request)
        href               (get-in request [:body :deployment :href])
        params             (u/id->request-params href)
        request-deployment {:params params, :nuvla/authn authn-info}
        response           (crud/retrieve request-deployment)]
    (if (= (:status response) 200)
      (-> response
          :body
          (select-keys [:module :data :name :description :tags]))
      (throw (r/ex-bad-request (str "cannot resolve " href))))))


(defn create-deployment
  [{:keys [body] :as request}]
  (cond
    (get-in body [:module :href]) (assoc body :module (resolve-module request))
    (get-in body [:deployment :href]) (resolve-deployment request)
    :else (logu/log-and-throw-400 "Request body is missing a module or a deployment href map!")))


(defn create-job
  [{:keys [id nuvlabox] :as resource} request action execution-mode]
  (a/throw-cannot-manage resource request)
  (let [active-claim (auth/current-active-claim request)
        low-priority (get-in request [:body :low-priority] false)
        {{job-id     :resource-id
          job-status :status} :body} (job/create-job
                                       id action
                                       (-> {:owners ["group/nuvla-admin"]}
                                           (a/acl-append :edit-acl active-claim)
                                           (a/acl-append :edit-data nuvlabox)
                                           (a/acl-append :manage nuvlabox))
                                       :priority (if low-priority 999 50)
                                       :execution-mode execution-mode)
        job-msg      (str action " " id " with async " job-id)]
    (when (not= job-status 201)
      (throw (r/ex-response
               (format "unable to create async job to %s deployment" action) 500 id)))
    (event-utils/create-event id job-msg (a/default-acl (auth/current-authentication request)))
    (r/map-response job-msg 202 id job-id)))


(defn can-delete?
  [{:keys [state] :as _resource}]
  (#{"CREATED" "STOPPED" "ERROR"} state))


(defn can-start?
  [{:keys [state] :as _resource}]
  (contains? #{"CREATED" "STOPPED"} state))


(defn can-stop?
  [{:keys [state] :as _resource}]
  (contains? #{"PENDING" "STARTING" "UPDATING" "STARTED" "ERROR"} state))


(defn can-update?
  [{:keys [state] :as _resource}]
  (contains? #{"STARTED" "ERROR"} state))


(defn can-create-log?
  [{:keys [state] :as _resource}]
  (contains? #{"STARTED" "UPDATING" "ERROR"} state))

(defn can-fetch-module?
  [{:keys [state] :as _resource}]
  (contains? #{"CREATED" "STOPPED"} state))


(defn create-log
  [{:keys [id] :as _resource} {:keys [body] :as request}]
  (let [session-id (auth/current-session-id request)
        opts       (select-keys body [:since :lines])
        service    (:service body)]
    (deployment-log/create-log id session-id service opts)))


(defn throw-can-not-do-action
  [{:keys [id state] :as resource} pred action]
  (if (pred resource)
    resource
    (throw (r/ex-response (format "invalid state (%s) for %s on %s" state action id) 409 id))))


(defn throw-can-not-access-registries-creds
  [{:keys [id registries-credentials] :as resource} request]
  (let [preselected-creds   (-> resource
                                (get-in [:module :content :registries-credentials] [])
                                set)
        creds-to-be-checked (set/difference (set registries-credentials) preselected-creds)]
    (if (seq creds-to-be-checked)
      (let [filter-cred (str "subtype='infrastructure-service-registry' and ("
                             (->> creds-to-be-checked
                                  (map #(str "id='" % "'"))
                                  (str/join " or "))
                             ")")
            {:keys [body]} (crud/query {:params      {:resource-name credential/resource-type}
                                        :cimi-params {:filter (parser/parse-cimi-filter filter-cred)
                                                      :last   0}
                                        :nuvla/authn (:nuvla/authn request)})]
        (if (< (get body :count 0)
               (count creds-to-be-checked))
          (throw (r/ex-response (format "some registries credentials for %s can't be accessed" id)
                                403 id))
          resource))
      resource)))


(defn count-payment-methods
  [{:keys [cards bank-accounts]}]
  (+ (count cards) (count bank-accounts)))


(defn has-defined-payment-methods?
  [{:keys [customer-id] :as _customer}]
  (-> customer-id
      pricing-impl/retrieve-customer
      pricing-impl/list-payment-methods-customer
      count-payment-methods
      pos?))


(defn throw-price-need-payment-method
  [{{:keys [price]} :module :as resource} request]
  (let [follow-trial? (boolean (:follow-customer-trial price))
        active-claim  (auth/current-active-claim request)]
    (if (or
          (nil? config-nuvla/*stripe-api-key*)
          (nil? price)
          (or
            (and follow-trial?
                 (user-utils/customer-has-trialing-subscription? active-claim))
            (-> request
                auth/current-active-claim
                user-utils/active-claim->customer
                has-defined-payment-methods?)))
      resource
      (throw (r/ex-response "Payment method is required!" 402)))))


(defn remove-delete
  [operations]
  (vec (remove #(= (name :delete) (:rel %)) operations)))


(defn create-stripe-subscription
  [active-claim {:keys [account-id price-id follow-customer-trial] :as _price} coupon]
  (let [customer-trial-end (when (and follow-customer-trial
                                      (user-utils/customer-has-trialing-subscription? active-claim))
                             (some-> active-claim
                                     user-utils/active-claim->subscription-map
                                     :trial-end
                                     time/date-from-str))
        one-day-trial      (time/from-now 24 :hours)
        trial-end          (if (and
                                 customer-trial-end
                                 (time/after? customer-trial-end one-day-trial))
                             customer-trial-end
                             one-day-trial)]
    (pricing-impl/create-subscription
      {"customer"                (some-> active-claim
                                         user-utils/active-claim->customer
                                         :customer-id)
       "items"                   [{"price" price-id}]
       "application_fee_percent" 20
       "trial_end"               (time/unix-timestamp-from-date trial-end)
       "coupon"                  coupon
       "transfer_data"           {"destination" account-id}})))


(defn some-id->resource
  [id request]
  (try
    (some-> id
            (crud/retrieve-by-id-as-admin)
            (a/throw-cannot-view request))
    (catch Exception _)))


(defn infra->nb-id
  [infra request]
  (let [parent-infra-group (some-> infra
                                   :parent
                                   (some-id->resource request)
                                   :parent)]
    (when (and
            (string? parent-infra-group)
            (str/starts-with? parent-infra-group "nuvlabox/"))
      parent-infra-group)))


(defn get-context
  [{:keys [target-resource] :as _resource} full]
  (let [deployment       (some-> target-resource :href crud/retrieve-by-id-as-admin)
        credential       (some-> deployment :parent crud/retrieve-by-id-as-admin)
        infra            (some-> credential :parent crud/retrieve-by-id-as-admin)
        registries-creds (when full
                           (some->> deployment :registries-credentials
                                    (map crud/retrieve-by-id-as-admin)))
        registries-infra (when full
                           (map (comp crud/retrieve-by-id-as-admin :parent) registries-creds))]
    (job-interface/get-context->response
      deployment
      credential
      infra
      registries-creds
      registries-infra)))


(defn merge-module-element
  [key-fn current-val-fn current resolved]
  (let [coll->map           (fn [val-fn coll] (into {} (map (juxt key-fn val-fn) coll)))
        resolved-params-map (coll->map identity resolved)
        valid-params-set    (set (map key-fn resolved))
        current-params-map  (->> current
                                 (filter (fn [entry] (valid-params-set (key-fn entry))))
                                 (coll->map current-val-fn))]
    (into [] (vals (merge-with merge resolved-params-map current-params-map)))))


(defn merge-module
  [{current-content :content :as current-module}
   {resolved-content :content :as resolved-module}]
  (let [params (merge-module-element :name #(select-keys % [:value])
                                     (:output-parameters current-content)
                                     (:output-parameters resolved-content))
        env    (merge-module-element :name #(select-keys % [:value])
                                     (:environmental-variables current-content)
                                     (:environmental-variables resolved-content))

        files  (merge-module-element :file-name #(select-keys % [:file-content])
                                     (:files current-content)
                                     (:files resolved-content))]
    (assoc resolved-module
      :content
      (cond-> (dissoc resolved-content :output-parameters :environmental-variables :files)
              (seq params) (assoc :output-parameters params)
              (seq env) (assoc :environmental-variables env)
              (seq files) (assoc :files files))
      :href (:id current-module))))


(defn create-subscription
  [request deployment price]
  (when (and config-nuvla/*stripe-api-key* price)
    (some-> (auth/current-active-claim request)
            (create-stripe-subscription price (:coupon deployment))
            (pricing-impl/get-id))))


(defn stop-subscription
  [deployment]
  (when config-nuvla/*stripe-api-key*
    (some-> deployment
            :subscription-id
            pricing-impl/retrieve-subscription
            (pricing-impl/cancel-subscription {"invoice_now" true}))))
