(ns com.sixsq.nuvla.server.resources.deployment.utils
  (:require
    [clojure.data.json :as json]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.es.common.utils :as escu]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.pricing.payment :as payment]
    [com.sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.event-context :as ec]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.credential-template-api-key :as cred-api-key]
    [com.sixsq.nuvla.server.resources.deployment-parameter :as dep-param]
    [com.sixsq.nuvla.server.resources.job.interface :as job-interface]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.module.utils :as module-utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as nuvlabox-utils]
    [com.sixsq.nuvla.server.resources.resource-log :as resource-log]
    [com.sixsq.nuvla.server.util.general :as gen-util]
    [com.sixsq.nuvla.server.util.response :as r]))


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
  (let [log-title (str "Propagating acl to deployment parameters of " deployment-id)]
    (try
     (when-let [bulk-op-data (some->> {:params      {:resource-name "deployment-parameter"}
                                       :cimi-params {:filter (cimi-params-impl/cimi-filter
                                                               {:filter (str "parent='" deployment-id "'")})
                                                     :select ["id"]}
                                       :nuvla/authn auth/internal-identity}
                                      crud/query
                                      :body
                                      :resources
                                      seq
                                      (mapcat (fn [{:keys [id] :as _dp}]
                                                [{:update {:_id (u/id->uuid id) :_index (escu/collection-id->index dep-param/resource-type)}}
                                                 {:doc {:acl acl}}])))]
       (try
         (let [response (db/bulk-operation dep-param/resource-type bulk-op-data)]
           (log/warn log-title ":" (escu/summarise-bulk-operation-response response)))
         (catch Exception e
           (log/errorf log-title "failed: " e))))
     (catch Exception e
       (log/errorf log-title "query failed: " e)))))


(defn create-job
  [{:keys [id nuvlabox] :as resource} request action execution-mode
   & {:keys [payload]}]
  (a/throw-cannot-manage resource request)
  (let [active-claim (auth/current-active-claim request)
        low-priority (get-in request [:body :low-priority] false)
        parent-job   (get-in request [:body :parent-job])
        {{job-id     :resource-id
          job-status :status} :body} (job-utils/create-job
                                       id action
                                       (-> {:owners ["group/nuvla-admin"]}
                                           (a/acl-append :edit-data nuvlabox)
                                           (a/acl-append :manage nuvlabox)
                                           (a/acl-append :view-data active-claim)
                                           (a/acl-append :manage active-claim))
                                       (auth/current-user-id request)
                                       :parent-job parent-job
                                       :priority (if low-priority 999 50)
                                       :execution-mode execution-mode
                                       :payload (json/write-str payload))
        job-msg      (str action " " id " with async " job-id)]
    (when (not= job-status 201)
      (throw (r/ex-response
               (format "unable to create async job to %s deployment" action) 500 id)))
    (ec/add-linked-identifier job-id)
    (r/map-response job-msg 202 id job-id)))


(defn can-delete?
  [{:keys [state] :as _resource}]
  (#{"CREATED" "STOPPED"} state))


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
  [{:keys [id nuvlabox] :as _resource} {:keys [body] :as request}]
  (let [session-id (auth/current-session-id request)
        opts       (select-keys body [:since :lines])
        components (:components body)
        acl        (cond-> {:owners   ["group/nuvla-admin"]
                            :edit-acl [session-id]}
                           nuvlabox (-> (a/acl-append :edit-data nuvlabox)
                                        (a/acl-append :manage nuvlabox)))]
    (resource-log/create-log id components acl opts)))

(defn throw-can-not-access-registries-creds
  [{:keys [registries-credentials] :as resource}]
  (let [preselected-creds   (-> resource
                                (get-in [:module :content :registries-credentials] [])
                                set)
        creds-to-be-checked (set/difference (set registries-credentials) preselected-creds)]
    (module-utils/throw-cannot-access-registries-credentials creds-to-be-checked (auth/get-owner-request resource))
    resource))


(defn throw-can-not-access-helm-repo-url
  [resource]
  (let [helm-repo-url (get-in resource [:module :content :helm-repo-url])]
    (module-utils/throw-can-not-access-helm-repo-url helm-repo-url (auth/get-owner-request resource))
    resource))

(defn throw-can-not-access-helm-repo-cred
  [resource]
  (let [cred (get-in resource [:module :content :helm-repo-cred])]
    (module-utils/throw-can-not-access-helm-repo-cred cred (auth/get-owner-request resource))
    resource))


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
        nuvlaedge-status (some-> nuvlaedge :nuvlabox-status crud/retrieve-by-id-as-admin
                                 (select-keys [:id :ip :network]))
        registries-creds (when full
                           (some->> deployment :registries-credentials
                                    (map crud/retrieve-by-id-as-admin)))
        registries-infra (when full
                           (map (comp crud/retrieve-by-id-as-admin :parent) registries-creds))
        module-content   (some-> deployment :module :content)
        helm-repo-cred   (some-> module-content :helm-repo-cred
                                 crud/retrieve-by-id-as-admin)
        helm-repo-url    (some-> module-content :helm-repo-url
                                 crud/retrieve-by-id-as-admin)]
    (job-interface/get-context->response
      deployment
      credential
      infra
      nuvlaedge
      nuvlaedge-status
      registries-creds
      registries-infra
      helm-repo-cred
      helm-repo-url)))

(defn on-cancel
  [{:keys [target-resource] :as _job}]
  (let [deployment-id (some-> target-resource :href)
        deployment    (some-> deployment-id crud/retrieve-by-id-as-admin)
        edit-request  {:params      (u/id->request-params deployment-id)
                       :body        (assoc deployment :state "ERROR")
                       :nuvla/authn auth/internal-identity}]
    (crud/edit edit-request)))

(defn keep-current-value
  [k current new]
  (if current
    (if-let [v (get current k)]
      (assoc new k v)
      (dissoc new k))
    new))

(defn keep-current-defined-key
  [pk k current-seq new-seq]
  (let [current-seq-indexed (gen-util/index-by current-seq pk)
        get-current         #(get current-seq-indexed (get % pk))]
    (mapv #(keep-current-value k (get-current %) %) new-seq)))

(defn keep-current-defined-values
  [current-seq new-seq]
  (keep-current-defined-key :name :value current-seq new-seq))

(defn keep-defined-file-contents
  [current-seq new-seq]
  (keep-current-defined-key :file-name :file-content current-seq new-seq))

(defn keep-module-defined-values
  [{defined-content :content :as _module_defined_values}
   {content :content :as module}]
  (let [params (keep-current-defined-values (:output-parameters defined-content)
                                            (:output-parameters content))
        env    (keep-current-defined-values (:environmental-variables defined-content)
                                            (:environmental-variables content))
        files  (keep-defined-file-contents
                 (:files defined-content)
                 (:files content))]
    (update module :content
            #(cond-> %
                     (seq params) (assoc :output-parameters params)
                     (seq env) (assoc :environmental-variables env)
                     (seq files) (assoc :files files)))))

(defn throw-when-payment-required
  [{{:keys [price] :as module} :module owner :owner :as deployment} request]
  (if (or (nil? config-nuvla/*stripe-api-key*)
          (a/is-admin? (auth/current-authentication request))
          (a/can-edit-data? module request)
          (case (:status (payment/active-claim->subscription owner))
            ("active" "past_due") true
            "trialing" (or (nil? price)
                           (:follow-customer-trial price)
                           (-> owner
                               payment/active-claim->s-customer
                               payment/can-pay?))
            false))
    deployment
    (payment/throw-payment-required)))

(defn cred-edited?
  [parent current-parent]
  (boolean (and parent (not= parent current-parent))))

(defn default-execution-mode
  [cred-id nuvlabox]
  (if nuvlabox
    (if (nuvlabox-utils/has-job-pull-support? nuvlabox)
      "pull"
      "mixed")
    (when cred-id
      "push")))

(defn get-execution-mode
  [current next cred-id nuvlabox]
  (or (:execution-mode next)
      (when (not= (:parent current) (:parent next))
        (default-execution-mode cred-id nuvlabox))
      (:execution-mode current)
      (default-execution-mode cred-id nuvlabox)))

(defn get-acl
  [{:keys [id nuvlabox owner] :as current} {:keys [acl] :as _next} nb-id]
  (-> (or acl (:acl current))
      (a/acl-append :owners owner)
      (a/acl-append :view-acl id)
      (a/acl-append :edit-data id)
      (a/acl-append :edit-data nb-id)
      (cond->
        (and (some? nuvlabox)
             (not= nb-id nuvlabox))
        (a/acl-remove nuvlabox))))


(defn add-api-endpoint
  [{:keys [api-endpoint] :as resource} {:keys [base-uri] :as _request}]
  (assoc resource :api-endpoint (or api-endpoint (str/replace-first base-uri #"/api/" ""))))