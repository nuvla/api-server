(ns sixsq.nuvla.server.resources.deployment.utils
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.pricing.payment :as payment]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-api-key]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.job.interface :as job-interface]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as nuvlabox-utils]
    [sixsq.nuvla.server.resources.resource-log :as resource-log]
    [sixsq.nuvla.server.util.response :as r]))


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
                          resource-log/resource-type}]
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


(defn can-detach?
  [{:keys [deployment-set] :as _resource}]
  (some? deployment-set))


(defn can-create-log?
  [{:keys [state] :as _resource}]
  (contains? #{"STARTED" "UPDATING" "ERROR"} state))


(defn create-log
  [{:keys [id] :as _resource} {:keys [body] :as request}]
  (let [session-id (auth/current-session-id request)
        opts       (select-keys body [:since :lines])
        components (:components body)
        acl        {:owners   ["group/nuvla-admin"]
                    :edit-acl [session-id]}]
    (resource-log/create-log id components acl opts)))


(defn throw-can-not-do-action-invalid-state
  [{:keys [id state] :as resource} pred action]
  (if (pred resource)
    resource
    (throw (r/ex-response (format "invalid state (%s) for %s on %s" state action id) 409 id))))

(defn throw-can-not-do-action
  [{:keys [id] :as resource} pred action]
  (if (pred resource)
    resource
    (throw (r/ex-response (format "cannot do action %s on %s" action id) 409 id))))


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


(defn remove-delete
  [operations]
  (vec (remove #(= (name :delete) (:rel %)) operations)))


(defn infra->nb-id
  [infra]
  (let [parent-infra-group (some-> infra
                                   :parent
                                   crud/retrieve-by-id-as-admin
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
        nuvlaedge        (some-> deployment :nuvlabox crud/retrieve-by-id-as-admin)
        nuvlaedge-status (some-> nuvlaedge  :nuvlabox-status crud/retrieve-by-id-as-admin)
        registries-creds (when full
                           (some->> deployment :registries-credentials
                                    (map crud/retrieve-by-id-as-admin)))
        registries-infra (when full
                           (map (comp crud/retrieve-by-id-as-admin :parent) registries-creds))]
    (job-interface/get-context->response
      deployment
      credential
      infra
      nuvlaedge
      nuvlaedge-status
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

(defn throw-when-payment-required
  [{{:keys [price] :as module} :module :as deployment} request]
  (if (or (nil? config-nuvla/*stripe-api-key*)
          (a/is-admin? (auth/current-authentication request))
          (let [active-claim (auth/current-active-claim request)]
            (or
              (a/can-edit-data? module request)
              (case (:status (payment/active-claim->subscription active-claim))
                ("active" "past_due") true
                "trialing" (or (nil? price)
                               (:follow-customer-trial price)
                               (-> active-claim
                                   payment/active-claim->s-customer
                                   payment/can-pay?))
                false))))
    deployment
    (payment/throw-payment-required)))

(defn cred-edited?
  [parent current-parent]
  (boolean (and parent (not= parent current-parent))))

(defn default-execution-mode
  [nuvlabox-id]
  (if nuvlabox-id
    (let [nuvlabox (crud/retrieve-by-id-as-admin nuvlabox-id)]
      (if (nuvlabox-utils/has-job-pull-support? nuvlabox) "pull" "mixed"))
    "push"))
