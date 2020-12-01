(ns sixsq.nuvla.server.resources.deployment.utils
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-api-key]
    [sixsq.nuvla.server.resources.customer :as customer]
    [sixsq.nuvla.server.resources.customer.utils :as customer-utils]
    [sixsq.nuvla.server.resources.deployment-log :as deployment-log]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.pricing.stripe :as stripe]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]))


(defn generate-api-key-secret
  [deployment-id authn-info]
  (let [request-api-key {:params      {:resource-name credential/resource-type}
                         :body        {:name        (str "API credential for " deployment-id)
                                       :description (str "generated API credential for " deployment-id)
                                       :parent      deployment-id
                                       :template    {:href (str "credential-template/" cred-api-key/method)}}
                         :nuvla/authn authn-info}
        {{:keys [status resource-id secret-key]} :body :as response} (crud/add request-api-key)]
    (when (= status 201)
      {:api-key    resource-id
       :api-secret secret-key})))


(defn assoc-api-credentials
  [deployment-id authn-info]
  (try
    (if-let [api-credentials (generate-api-key-secret deployment-id authn-info)]
      (let [edit-request {:params      (u/id->request-params deployment-id)
                          :body        {:api-credentials api-credentials}
                          :nuvla/authn authn-info}
            {:keys [status] :as response} (crud/edit edit-request)]
        (when (not= status 200)
          (log/error "could not add api key/secret to" deployment-id response)))
      (log/error "could not create api key/secret for" deployment-id))
    (catch Exception e
      (log/error (str "exception when creating api key/secret for " deployment-id ": " e)))))


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


(defn resolve-module [request]
  (let [authn-info     (auth/current-authentication request)
        href           (get-in request [:body :module :href])
        params         (u/id->request-params href)
        module-request {:params params, :nuvla/authn authn-info}
        response       (crud/retrieve module-request)
        module-body    (:body response)]
    (if (= (:status response) 200)
      (-> module-body
          (dissoc :versions :operations)
          (std-crud/resolve-hrefs authn-info true)
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
  [{:keys [id] :as resource} request action]
  (a/throw-cannot-manage resource request)
  (let [active-claim (auth/current-active-claim request)
        {{job-id     :resource-id
          job-status :status} :body} (job/create-job id action
                                                     {:owners   ["group/nuvla-admin"]
                                                      :edit-acl [active-claim]}
                                                     :priority 50)
        job-msg      (str action " " id " with async " job-id)]
    (when (not= job-status 201)
      (throw (r/ex-response
               (format "unable to create async job to %s deployment" action) 500 id)))
    (event-utils/create-event id job-msg (a/default-acl (auth/current-authentication request)))
    (r/map-response job-msg 202 id job-id)))


(defn can-delete?
  [{:keys [state] :as resource}]
  (#{"CREATED" "STOPPED" "ERROR"} state))


(defn can-start?
  [{:keys [state] :as resource}]
  (contains? #{"CREATED" "STOPPED"} state))


(defn can-stop?
  [{:keys [state] :as resource}]
  (contains? #{"STARTING" "UPDATING" "STARTED" "ERROR"} state))


(defn can-update?
  [{:keys [state] :as resource}]
  (contains? #{"STARTED"} state))


(defn can-create-log?
  [{:keys [state] :as resource}]
  (contains? #{"STARTED" "UPDATING" "ERROR"} state))

(defn can-fetch-module?
  [{:keys [state] :as resource}]
  (contains? #{"CREATED" "STOPPED"} state))


(defn create-log
  [{:keys [id] :as resource} {:keys [body] :as request}]
  (let [session-id (auth/current-session-id request)
        opts       (select-keys body [:since :lines])
        service    (:service body)]
    (deployment-log/create-log id session-id service opts)))


(defn merge-module-element
  [key-fn current-val-fn current resolved]
  (let [coll->map           (fn [val-fn coll] (into {} (map (juxt key-fn val-fn) coll)))
        resolved-params-map (coll->map identity resolved)
        valid-params-set    (set (map key-fn resolved))
        current-params-map  (->> current
                                 (filter (fn [entry] (valid-params-set (key-fn entry))))
                                 (coll->map current-val-fn))]
    (vals (merge-with merge resolved-params-map current-params-map))))

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
              (seq files) (assoc :files files)))))


(defn fetch-module
  [{:keys [module] :as resource} request]
  (let [specific-version (get-in request [:body :module :href])]
    (->> (cond-> resource
                 specific-version (assoc-in [:module :href] specific-version))
        (assoc request :body)
        (resolve-module)
        (merge-module module)
        (assoc resource :module))))


(defn throw-can-not-do-action
  [{:keys [id state] :as resource} pred action]
  (if (pred resource)
    resource
    (throw (r/ex-response (format "invalid state (%s) for %s on %s" state action id) 409 id))))


(defn throw-can-not-access-registries-creds
  [{:keys [id registries-credentials] :as resource} request]
  (let [preselected-creds (-> resource
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


(defn throw-price-need-payment-method
  [{{:keys [price]} :module :as resource} request]
  (if price
    (let [count-pm              (-> request
                                    auth/current-active-claim
                                    customer/active-claim->customer
                                    :customer-id
                                    stripe/retrieve-customer
                                    customer-utils/list-payment-methods
                                    count-payment-methods)]
      (if (pos? count-pm)
        resource
        (throw (r/ex-response "Payment method is required!" 402))))
    resource))


(defn remove-delete
  [operations]
  (vec (remove #(= (name :delete) (:rel %)) operations)))
