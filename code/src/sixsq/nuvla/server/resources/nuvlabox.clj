(ns sixsq.nuvla.server.resources.nuvlabox
  "
The core `nuvlabox` resource that contains only those attributes required in
all subtypes of this resource. Versioned subclasses define the attributes for a
particular NuvlaBox release.
"
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential.vpn-utils :as vpn-utils]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.job.interface :as job-interface]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.nuvlabox.workflow-utils :as wf-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox :as nuvlabox]
    [sixsq.nuvla.server.resources.user.utils :as user-utils]
    [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


(def ^:const state-new "NEW")


(def ^:const state-activated "ACTIVATED")


(def ^:const state-commissioned "COMMISSIONED")


(def ^:const state-decommissioning "DECOMMISSIONING")


(def ^:const state-decommissioned "DECOMMISSIONED")


(def ^:const state-error "ERROR")


;;
;; If version is not specified use, the latest version.
;;
;; WARNING: This must be updated when new nuvlabox schemas are added!
;;
(def ^:const latest-version 2)


(def ^:const default-refresh-interval 90)


(def actions [{:name           "activate"
               :uri            "activate"
               :description    "activate the nuvlabox"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "commission"
               :uri            "commission"
               :description    "commission the nuvlabox"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "decommission"
               :uri            "decommission"
               :description    "decommission the nuvlabox"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "check-api"
               :uri            "check-api"
               :description    "check nuvlabox api"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "reboot"
               :uri            "reboot"
               :description    "reboot the nuvlabox"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name             "add-ssh-key"
               :uri              "add-ssh-key"
               :description      "add an ssh key to the nuvlabox"
               :method           "POST"
               :input-message    "application/json"
               :output-message   "application/json"
               :input-parameters [{:name        "credential"
                                   :type        "string"
                                   :description "credential id to be added"}]}

              {:name             "revoke-ssh-key"
               :uri              "revoke-ssh-key"
               :description      "revoke an ssh key to the nuvlabox"
               :method           "POST"
               :input-message    "application/json"
               :output-message   "application/json"
               :input-parameters [{:name        "credential"
                                   :type        "string"
                                   :description "credential id to be added"}]}

              {:name             "update-nuvlabox"
               :uri              "update-nuvlabox"
               :description      "update nuvlabox engine"
               :method           "POST"
               :input-message    "application/json"
               :output-message   "application/json"
               :input-parameters [{:name "nuvlabox-release"
                                   :type "string"}
                                  {:name "payload"
                                   :type "string"}]}

              {:name           "assemble-playbooks"
               :uri            "assemble-playbooks"
               :description    "assemble the nuvlabox playbooks for execution"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "enable-host-level-management"
               :uri            "enable-host-level-management"
               :description    "enables the use of nuvlabox playbooks for host level management"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "disable-host-level-management"
               :uri            "disable-host-level-management"
               :description    "disables the use of nuvlabox playbooks for host level management"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}
              ])


;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given nuvlabox resource against a specific
           version of the schema."
          :version)


(defmethod validate-subtype :default
  [{:keys [version] :as _resource}]
  (if version
    (throw (r/ex-bad-request (str "unsupported nuvlabox version: " version)))
    (throw (r/ex-bad-request "missing nuvlabox version"))))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))


;;
;; The nuvlabox id (also used as a claim in the credential
;; given to the NuvlaBox) will have the "manage" right
;; initially.
;;

(defmethod crud/add-acl resource-type
  [{:keys [id vpn-server-id owner] :as resource} _request]
  (let [acl (cond-> {:owners    ["group/nuvla-admin"]
                     :manage    [id]
                     :view-data [id]
                     :edit-acl  [owner]}
                    vpn-server-id (assoc :view-acl [vpn-server-id]))]
    (assoc resource :acl acl)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [version refresh-interval vpn-server-id owner]
     :or   {version          latest-version
            refresh-interval default-refresh-interval}
     :as   body} :body :as request}]
  (let [authn-info (auth/current-authentication request)
        is-admin?  (a/is-admin? authn-info)]
    (when vpn-server-id
      (let [vpn-service (vpn-utils/get-service vpn-server-id)]
        (vpn-utils/check-service-subtype vpn-service)))

    (user-utils/throw-user-hasnt-active-subscription request)

    (let [nb-owner     (if is-admin? (or owner "group/nuvla-admin")
                                     (auth/current-active-claim request))
          new-nuvlabox (assoc body :version version
                                   :state state-new
                                   :refresh-interval refresh-interval
                                   :owner nb-owner)
          resp         (add-impl (assoc request :body new-nuvlabox))]
      (ka-crud/publish-on-add resource-type resp)
      resp)))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(defn value-changed?
  [current-nb updated-nb field-key]
  (and (some? (get updated-nb field-key))
       (not= (get updated-nb field-key) (get current-nb field-key))))


(defn should-propagate-changes?
  [current-nb updated-nb]
  (->> [:acl :name :capabilities :nuvlabox-status
        :infrastructure-service-group :credential-api-key]
       (some (partial value-changed? current-nb updated-nb))
       boolean))


(defn edit-subresources
  [current-nb updated-nb]
  (when (should-propagate-changes? current-nb updated-nb)
    (let [{:keys [id name acl nuvlabox-status
                  infrastructure-service-group credential-api-key capabilities]
           :as   nuvlabox} (acl-utils/normalize-acl-for-resource updated-nb)]

      (when nuvlabox-status
        (wf-utils/update-nuvlabox-status nuvlabox-status nuvlabox))

      (when infrastructure-service-group

        (wf-utils/update-infrastructure-service-group infrastructure-service-group nuvlabox)

        (let [swarm-id (wf-utils/update-coe-service id name acl infrastructure-service-group nil nil capabilities "swarm")]
          (wf-utils/update-coe-cred id name acl swarm-id nil nil nil "infrastructure-service-swarm")
          (wf-utils/update-swarm-token id name acl swarm-id "MANAGER" nil)
          (wf-utils/update-swarm-token id name acl swarm-id "WORKER" nil))

        (let [k8s-id (wf-utils/update-coe-service id name acl infrastructure-service-group nil nil capabilities "kubernetes")]
          (wf-utils/update-coe-cred id name acl k8s-id nil nil nil "infrastructure-service-kubernetes"))

        (let [minio-id (wf-utils/update-minio-service id name acl infrastructure-service-group nil)]
          (wf-utils/update-minio-cred id name acl minio-id nil nil)))

      (when credential-api-key
        (wf-utils/update-nuvlabox-api-key credential-api-key nuvlabox))

      (when (value-changed? current-nb updated-nb :acl)
        (wf-utils/update-peripherals id acl))

      (when (value-changed? current-nb updated-nb :acl)
        (wf-utils/update-playbooks id acl)))))


(def edit-impl (std-crud/edit-fn resource-type))

(defn restricted-body
  [{:keys [name description location tags ssh-keys capabilities acl] :as _body}
   {:keys [id owner vpn-server-id] :as existing-resource}]
  (cond-> (dissoc existing-resource :name :description :location :tags :ssh-keys :capabilities :acl)
          name (assoc :name name)
          description (assoc :description description)
          location (assoc :location location)
          tags (assoc :tags tags)
          ssh-keys (assoc :ssh-keys ssh-keys)
          capabilities (assoc :capabilities capabilities)
          acl (assoc
                :acl (merge
                       (select-keys acl [:view-meta :edit-data :edit-meta :delete])
                       {:owners    ["group/nuvla-admin"]
                        :edit-acl  (vec (distinct (concat (:edit-acl acl) [owner])))
                        :view-acl  (vec (distinct (concat (:view-acl acl) (when vpn-server-id
                                                                            [vpn-server-id]))))
                        :view-data (vec (distinct (concat (:view-data acl) [id])))
                        :manage    (vec (distinct (concat (:manage acl) [id])))}))))


(defmethod crud/edit resource-type
  [{{uuid :uuid} :params :as request}]
  (let [authn-info    (auth/current-authentication request)
        is-not-admin? (not (a/is-admin? authn-info))
        current       (-> (str resource-type "/" uuid)
                          (db/retrieve (assoc-in request [:cimi-params :select] nil))
                          (a/throw-cannot-edit request))
        {updated-nb :body :as resp} (-> request
                                        (cond-> is-not-admin? (assoc :body (-> request
                                                                               (u/delete-attributes current)
                                                                               (restricted-body current))))
                                        edit-impl)]
    (ka-crud/publish-on-edit resource-type resp)
    (edit-subresources current updated-nb)
    resp))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


(defn verify-deletable-state
  [{:keys [id state] :as resource}]
  (if (#{state-new state-decommissioned state-error} state)
    resource
    (throw (r/ex-response (str "cannot delete nuvlabox in state " state) 409 id))))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)]
    (try
      (-> (db/retrieve id request)
          (a/throw-cannot-delete request)
          verify-deletable-state)
      (let [resp (delete-impl request)]
        (ka-crud/publish-tombstone resource-type id)
        resp)
      (catch Exception e
        (or (ex-data e) (throw e))))))


;;
;; Activate operation
;;

(defn activate
  [{:keys [id state] :as nuvlabox}]
  (if (= state state-new)
    (do
      (log/warn "activating nuvlabox:" id)
      (let [activated-nuvlabox (-> nuvlabox
                                   (assoc :state state-activated)
                                   wf-utils/create-nuvlabox-status
                                   wf-utils/create-infrastructure-service-group)]
        activated-nuvlabox))
    (logu/log-and-throw-400 (str "invalid state for activation: " state))))


(defmethod crud/do-action [resource-type "activate"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)
          [nuvlabox-activated api-secret-info] (-> (db/retrieve id request)
                                                   (activate)
                                                   u/update-timestamps
                                                   (wf-utils/create-nuvlabox-api-key ""))]


      (db/edit nuvlabox-activated request)

      (r/json-response api-secret-info))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Commission operation
;;

(defmulti commission
          "Recreates the infrastructure-service(s), credentials, etc. that are
           associated with this nuvlabox. The resources that are created
           depend on the version of nuvlabox-* resources being used."
          (fn [resource _request] (:version resource)))


(defmethod commission :default
  [{:keys [version] :as _resource} _request]
  (if version
    (throw (r/ex-bad-request (str "unsupported nuvlabox version for commission action: " version)))
    (throw (r/ex-bad-request "missing nuvlabox version for commission action"))))


(defmethod crud/do-action [resource-type "commission"]
  [{{uuid :uuid} :params body :body :as request}]
  (let [id (str resource-type "/" uuid)]
    (try
      (let [tags         (some-> body :tags set vec)
            capabilities (some-> body :capabilities set vec)
            ssh-keys     (some-> body :ssh-keys set vec)
            nuvlabox     (-> (db/retrieve id request)
                             (assoc :state state-commissioned)
                             (cond-> tags (assoc :tags tags)
                                     capabilities (assoc :capabilities capabilities)
                                     ssh-keys (assoc :ssh-keys ssh-keys))
                             u/update-timestamps
                             crud/validate)]
        (-> nuvlabox
            (a/throw-cannot-manage request)
            (commission request))

        (db/edit nuvlabox request)

        (r/map-response "commission executed successfully" 200))
      (catch Exception e
        (or (ex-data e) (throw e))))))


;;
;; Decommission operation
;;

(defn restrict-acl
  "Updates the given acl by giving the view-acl, manage, and delete rights to
   all owners, removing all edit-* rights, and setting the owners to only
   [\"group/nuvla-admin\"]."
  [{:keys [owners] :as acl}]
  (-> acl
      (dissoc :edit-meta :edit-data :edit-acl)
      (assoc :owners ["group/nuvla-admin"])
      (update-in [:view-acl] concat owners)
      (update-in [:manage] concat owners)
      (update-in [:delete] concat owners)))


(defmulti decommission-sync
          "Executes the synchronous tasks associated with decommissioning a
           nuvlabox resource. This must always return the value of the resource
           that was passed in."
          (fn [resource _request] (:version resource)))


(defmethod decommission-sync :default
  [{:keys [id acl] :as resource} request]
  (let [updated-acl (restrict-acl acl)]
    (-> resource
        (assoc :state state-decommissioning
               :acl updated-acl)
        u/update-timestamps
        (u/set-updated-by request)
        (db/edit request))

    ;; read back the updated resource to ensure that ACL is fully normalized
    (crud/retrieve-by-id-as-admin id)))


(defmulti decommission-async
          "Creates a job to handle all the asynchronous clean up that is
           needed when decommissioning a nuvlabox."
          (fn [resource _request] (:version resource)))


(defmethod decommission-async :default
  [{:keys [id acl] :as _resource} _request]
  (try
    (let [{{job-id     :resource-id
            job-status :status} :body} (job/create-job id "decommission_nuvlabox"
                                                       acl
                                                       :priority 50)
          job-msg (str "decommissioning " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response
                 "unable to create async job to decommission nuvlabox resources" 500 id)))
      (event-utils/create-event id job-msg acl)
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "decommission"]
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)]
    (try
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (decommission-sync request)
          (decommission-async request))
      (catch Exception e
        (or (ex-data e) (throw e))))))


;;
;;
;; NuvlaBox API operations
;;
;;

;;
;; Check API action
;;

(defn check-api
  [{:keys [id state acl] :as _nuvlabox}]
  (if (= state state-commissioned)
    (do
      (log/warn "Checking API for NuvlaBox:" id)
      (try
        (let [{{job-id     :resource-id
                job-status :status} :body} (job/create-job id "check_nuvlabox_api"
                                                           acl
                                                           :priority 50)
              job-msg (str "checking the API for NuvlaBox " id " with async " job-id)]
          (when (not= job-status 201)
            (throw (r/ex-response
                     "unable to create async job to check nuvlabox api" 500 id)))
          (event-utils/create-event id job-msg acl)
          (r/map-response job-msg 202 id job-id))
        (catch Exception e
          (or (ex-data e) (throw e)))))
    (logu/log-and-throw-400 (str "invalid state for NuvlaBox actions: " state))))


(defmethod crud/do-action [resource-type "check-api"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (check-api)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Reboot action
;;

(defn reboot
  [{:keys [id state acl] :as nuvlabox}]
  (if (= state state-commissioned)
    (do
      (log/warn "Rebooting NuvlaBox:" id)
      (try
        (let [{{job-id     :resource-id
                job-status :status} :body} (job/create-job id "reboot_nuvlabox"
                                                           (-> acl
                                                               (a/acl-append :edit-data id)
                                                               (a/acl-append :manage id))
                                                           :priority 50
                                                           :execution-mode (utils/get-execution-mode nuvlabox))
              job-msg (str "sending reboot request to NuvlaBox " id " with async " job-id)]
          (when (not= job-status 201)
            (throw (r/ex-response
                     "unable to create async job to reboot nuvlabox" 500 id)))
          (event-utils/create-event id job-msg acl)
          (r/map-response job-msg 202 id job-id))
        (catch Exception e
          (or (ex-data e) (throw e)))))
    (logu/log-and-throw-400 (str "invalid state for NuvlaBox actions: " state))))


(defmethod crud/do-action [resource-type "reboot"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (reboot)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Cluster action
;;


(defn cluster-nuvlabox
  [{:keys [id state acl] :as nuvlabox}
   cluster-action nuvlabox-manager-status token advertise-addr]
  (if (= state state-commissioned)
    (do
      (when (and (str/starts-with? cluster-action "join-") (nil? nuvlabox-manager-status))
        (logu/log-and-throw-400 "To join a cluster you need to specify the managing NuvlaBox"))

      (log/warn "Running cluster action " cluster-action)
      (try
        (let [payload (cond-> {:cluster-action cluster-action
                               :token          token
                               :advertise-addr advertise-addr}
                              (seq nuvlabox-manager-status) (assoc :nuvlabox-manager-status
                                                                   nuvlabox-manager-status))
              {{job-id     :resource-id
                job-status :status} :body} (job/create-job
                                             id (str "nuvlabox_cluster_" (str/replace cluster-action #"-" "_"))
                                             (-> acl
                                                 (a/acl-append :edit-data id)
                                                 (a/acl-append :manage id))
                                             :priority 50
                                             :execution-mode (utils/get-execution-mode nuvlabox)
                                             :payload (json/write-str payload))
              job-msg (str "running cluster action " cluster-action " on NuvlaBox " id
                           ", with async " job-id)]
          (when (not= job-status 201)
            (throw (r/ex-response "unable to create async job to cluster NuvlaBox" 500 id)))
          (event-utils/create-event id job-msg acl)
          (r/map-response job-msg 202 id job-id))
        (catch Exception e
          (or (ex-data e) (throw e)))))
    (logu/log-and-throw-400 (str "invalid state for NuvlaBox actions: " state))))


(defmethod crud/do-action [resource-type "cluster-nuvlabox"]
  [{{uuid :uuid} :params {:keys [cluster-action nuvlabox-manager-status token advertise-addr]} :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (when-not (empty? nuvlabox-manager-status)
        (-> (db/retrieve (:id nuvlabox-manager-status) request)
            (a/throw-cannot-view request)))
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (cluster-nuvlabox cluster-action nuvlabox-manager-status token advertise-addr)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Add ssh-key action
;;


(defn add-ssh-key
  [{:keys [id state acl] :as nuvlabox} ssh-credential]
  (if (= state state-commissioned)
    (do
      (log/warn "Adding new SSH key for NuvlaBox:" id)
      (try
        (let [cred-id (:id ssh-credential)
              {{job-id     :resource-id
                job-status :status} :body} (job/create-job
                                             id "nuvlabox_add_ssh_key"
                                             (-> acl
                                                 (a/acl-append :edit-data id)
                                                 (a/acl-append :manage id))
                                             :affected-resources [{:href cred-id}]
                                             :priority 50
                                             :execution-mode (utils/get-execution-mode nuvlabox))
              job-msg (str "asking NuvlaBox "
                           id " to add SSH key "
                           cred-id " with async " job-id)]
          (when (not= job-status 201)
            (throw (r/ex-response
                     "unable to create async job to add SSH key to NuvlaBox" 500 id)))
          (event-utils/create-event id job-msg acl)
          (r/map-response (or (:private-key ssh-credential) job-msg) 202 id job-id))
        (catch Exception e
          (or (ex-data e) (throw e)))))
    (logu/log-and-throw-400 (str "invalid state for NuvlaBox actions: " state))))


(defmethod crud/do-action [resource-type "add-ssh-key"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id          (str resource-type "/" uuid)
          ssh-cred-id (:credential body)
          nuvlabox    (db/retrieve id request)
          acl         (:acl nuvlabox)
          credential  (if ssh-cred-id
                        (db/retrieve ssh-cred-id request)
                        (wf-utils/create-ssh-key
                          {:acl      acl
                           :template {:href "credential-template/generate-ssh-key"}}))]

      (-> (db/retrieve (:id credential) request)
          (a/throw-cannot-view request))
      (-> nuvlabox
          (a/throw-cannot-manage request)
          (add-ssh-key credential)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn get-context-ssh-credential
  [{:keys [affected-resources] :as _job}]
  (let [credential (some->> affected-resources
                            (some (fn [{:keys [href]}]
                                    (when (str/starts-with? href "credential/") href)))
                            crud/retrieve-by-id-as-admin)]
    (job-interface/get-context->response credential)))


(defmethod job-interface/get-context ["nuvlabox" "nuvlabox_add_ssh_key"]
  [resource]
  (get-context-ssh-credential resource))


;;
;; Revoke ssh-key action
;;


(defn revoke-ssh-key
  [{:keys [id state acl] :as nuvlabox} ssh-credential-id]
  (if (= state state-commissioned)
    (if (nil? ssh-credential-id)
      (logu/log-and-throw-400 "SSH credential ID is missing")
      (do
        (log/warn "Removing SSH key " ssh-credential-id " from NuvlaBox " id)
        (try
          (let [{{job-id     :resource-id
                  job-status :status} :body} (job/create-job
                                               id "nuvlabox_revoke_ssh_key"
                                               (-> acl
                                                   (a/acl-append :edit-data id)
                                                   (a/acl-append :manage id))
                                               :affected-resources [{:href ssh-credential-id}]
                                               :priority 50
                                               :execution-mode (utils/get-execution-mode nuvlabox))
                job-msg (str "removing SSH key " ssh-credential-id
                             " from NuvlaBox " id " with async " job-id)]
            (when (not= job-status 201)
              (throw (r/ex-response
                       "unable to create async job to remove SSH key from NuvlaBox" 500 id)))
            (event-utils/create-event id job-msg acl)
            (r/map-response job-msg 202 id job-id))
          (catch Exception e
            (or (ex-data e) (throw e))))))
    (logu/log-and-throw-400 (str "invalid state for NuvlaBox actions: " state))))


(defmethod crud/do-action [resource-type "revoke-ssh-key"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id          (str resource-type "/" uuid)
          ssh-cred-id (:credential body)]
      (-> (db/retrieve ssh-cred-id request)
          (a/throw-cannot-view request))
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (revoke-ssh-key ssh-cred-id)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod job-interface/get-context ["nuvlabox" "nuvlabox_revoke_ssh_key"]
  [resource]
  (get-context-ssh-credential resource))


;;
;; Update NuvlaBox Engine action
;;

(defn update-nuvlabox
  [{:keys [id state acl] :as nuvlabox} nb-release-id payload]
  (if (= state state-commissioned)
    (if (nil? nb-release-id)
      (logu/log-and-throw-400 "Target NuvlaBox release is missing")
      (do
        (log/warn "Updating NuvlaBox " id)
        (try
          (let [{{job-id     :resource-id
                  job-status :status} :body} (job/create-job
                                               id "nuvlabox_update"
                                               (-> acl
                                                   (a/acl-append :edit-data id)
                                                   (a/acl-append :manage id))
                                               :affected-resources [{:href nb-release-id}]
                                               :priority 50
                                               :execution-mode (utils/get-execution-mode nuvlabox)
                                               :payload (when-not (str/blank? payload) payload))
                job-msg (str "updating NuvlaBox " id " with target release " nb-release-id
                             ", with async " job-id)]
            (when (not= job-status 201)
              (throw (r/ex-response "unable to create async job to update NuvlaBox" 500 id)))
            (event-utils/create-event id job-msg acl)
            (r/map-response job-msg 202 id job-id))
          (catch Exception e
            (or (ex-data e) (throw e))))))
    (logu/log-and-throw-400 (str "invalid state for NuvlaBox actions: " state))))


(defmethod crud/do-action [resource-type "update-nuvlabox"]
  [{{uuid :uuid} :params {:keys [nuvlabox-release payload]} :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve nuvlabox-release request)
          (a/throw-cannot-view request))
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (update-nuvlabox nuvlabox-release payload)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Assemble NuvlaBox playbooks and prepare them for execution on the edge device
;;


(defn assemble-playbooks
  [{:keys [id state] :as nuvlabox}]
  (if (#{state-decommissioned state-new} state)
    (logu/log-and-throw-400 (str "invalid state for getting and assembling NuvlaBox playbooks: " state))
    (try
      (log/warn "Assembling playbooks for execution, for NuvlaBox " id)
      (let [emergency-playbooks (seq (utils/get-playbooks id "EMERGENCY"))]
        (when emergency-playbooks
          (log/warn "Running one-off emergency playbooks for NuvlaBox " id " and disabling them")
          (doall
            (map (fn [playbook-id]
                   (-> {:params      {:uuid          (u/id->uuid playbook-id)
                                      :resource-name "nuvlabox-playbook"}
                        :body        {:enabled false}
                        :nuvla/authn auth/internal-identity}
                     (crud/edit)))
              (map :id emergency-playbooks))))
        (r/text-response (utils/wrap-and-pipe-playbooks (or emergency-playbooks
                                                            (utils/get-playbooks id)))))
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type "assemble-playbooks"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (assemble-playbooks)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Enables the nuvlabox-playbooks and provides the mechanism for host-level management
;;


(defn enable-host-level-management
  [{:keys [id host-level-management-api-key] :as nuvlabox} request]
  (if host-level-management-api-key
    (logu/log-and-throw-400 (str "host level management is already enabled for NuvlaBox " id))
    (try
      (let [[_ credential] (wf-utils/create-nuvlabox-api-key nuvlabox "[nuvlabox-playbook]")
            updated_nuvlabox (assoc nuvlabox :host-level-management-api-key (:api-key credential))]
        (db/edit updated_nuvlabox request)

        (r/text-response (utils/compose-cronjob credential id)))
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type "enable-host-level-management"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (enable-host-level-management request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Disables the nuvlabox-playbooks
;;


(defn disable-host-level-management
  [{:keys [id host-level-management-api-key] :as nuvlabox}]
  (if host-level-management-api-key
    (do
      (log/warn "Disabling host-level management for NuvlaBox " id)
      (try
        (wf-utils/delete-resource host-level-management-api-key auth/internal-identity)
        (-> {:cimi-params {:select ["host-level-management-api-key"]}
             :params      {:uuid          (u/id->uuid id)
                           :resource-name "nuvlabox"}
             :body        {}
             :nuvla/authn auth/internal-identity}
            (crud/edit))
        (catch Exception e
          (or (ex-data e) (throw e)))))
    (logu/log-and-throw-400 (str "host-level management is already disabled for NuvlaBox " id))))


(defmethod crud/do-action [resource-type "disable-host-level-management"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (disable-host-level-management)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Enable the emergency playbooks so that the next host management cycle can do a one-off disaster recovery
;;


(defn enable-emergency-playbooks
  [{:keys [id state] :as nuvlabox} emergency-playbooks-ids current-authn]
  (if (#{state-decommissioned state-new} state)
    (logu/log-and-throw-400 (str "invalid state for enabling emergency NuvlaBox playbooks: " state))
    (try
      (log/warn "Enabling emergency playbooks for one-off execution, for NuvlaBox " id)
      (doall (map (fn [playbook-id]
                     (do
                       (-> {:params      {:uuid          (u/id->uuid playbook-id)
                                          :resource-name "nuvlabox-playbook"}
                            :body        {:enabled true}
                            :nuvla/authn current-authn}
                         (crud/edit))))
                emergency-playbooks-ids))
      (r/json-response {:enable-emergency-playbooks emergency-playbooks-ids})
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type "enable-emergency-playbooks"]
  [{{uuid :uuid} :params {:keys [emergency-playbooks-ids]} :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
        (a/throw-cannot-manage request)
        (enable-emergency-playbooks emergency-playbooks-ids (auth/current-authentication request))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Set operation
;;

;;
;; operations for states for owner are:
;;
;;                edit delete activate commission decommission
;; NEW             Y     Y       Y
;; ACTIVATED       Y                       Y           Y
;; DECOMMISSIONING Y                                   Y
;; DECOMMISSIONED  Y     Y
;; ERROR           Y     Y                             Y
;;

(defmethod crud/set-operations resource-type
  [{:keys [id state] :as resource} request]
  (let [edit-op            (u/operation-map id :edit)
        delete-op          (u/operation-map id :delete)
        activate-op        (u/action-map id :activate)
        commission-op      (u/action-map id :commission)
        decommission-op    (u/action-map id :decommission)
        check-api-op       (u/action-map id :check-api)
        reboot-op          (u/action-map id :reboot)
        add-ssh-key-op     (u/action-map id :add-ssh-key)
        revoke-ssh-key-op  (u/action-map id :revoke-ssh-key)
        update-nuvlabox-op (u/action-map id :update-nuvlabox)
        cluster-nb-op      (u/action-map id :cluster-nuvlabox)
        assemble-pb-op     (u/action-map id :assemble-playbooks)
        enable-host-mgmt-op   (u/action-map id :enable-host-level-management)
        disable-host-mgmt-op  (u/action-map id :disable-host-level-management)
        enable-emergency-op   (u/action-map id :enable-emergency-playbooks)
        ops                (cond-> []
                                   (a/can-edit? resource request) (conj edit-op)
                                   (and (a/can-delete? resource request)
                                        (#{state-new
                                           state-decommissioned
                                           state-error} state)) (conj delete-op)
                                   (and (a/can-manage? resource request)
                                        (#{state-new} state)) (conj activate-op)
                                   (and (a/can-manage? resource request)
                                        (#{state-activated
                                           state-commissioned} state)) (conj commission-op)
                                   (and (a/can-manage? resource request)
                                        (not= state state-new)
                                        (not= state state-decommissioned)) (conj decommission-op)
                                   (and (a/can-manage? resource request)
                                        (#{state-commissioned} state)
                                        (< (:version resource) 2)) (conj check-api-op)
                                   (and (a/can-manage? resource request)
                                        (#{state-commissioned} state)) (conj add-ssh-key-op)
                                   (and (a/can-manage? resource request)
                                        (#{state-commissioned} state)) (conj revoke-ssh-key-op)
                                   (and (a/can-manage? resource request)
                                        (#{state-commissioned} state)) (conj update-nuvlabox-op)
                                   (and (a/can-manage? resource request)
                                        (#{state-commissioned} state)
                                        (>= (:version resource) 2)) (conj cluster-nb-op)
                                   (and (a/can-manage? resource request)
                                        (#{state-commissioned} state)) (conj reboot-op)
                                   (and (a/can-manage? resource request)
                                        (not= state state-new)
                                        (not= state state-decommissioned)) (conj assemble-pb-op)
                                   (and (a/can-manage? resource request)
                                        (not= state state-new)
                                        (not= state state-decommissioned)) (conj enable-emergency-op)
                                   (and (a/can-manage? resource request)
                                        (nil? (:host-level-management-api-key resource))) (conj enable-host-mgmt-op)
                                   (and (a/can-manage? resource request)
                                        (contains? resource :host-level-management-api-key)) (conj disable-host-mgmt-op))]
    (assoc resource :operations ops)))

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nuvlabox/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nuvlabox/schema)
  (md/register resource-metadata))


