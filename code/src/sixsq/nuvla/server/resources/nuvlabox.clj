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
    [sixsq.nuvla.server.resources.resource-log :as resource-log]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.common-body :as common-body]
    [sixsq.nuvla.server.resources.spec.nuvlabox :as nuvlabox]
    [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user"]
                     :bulk-action ["group/nuvla-user"]})


;;
;; If version is not specified use, the latest version.
;;
;; WARNING: This must be updated when new nuvlabox schemas are added!
;;
(def ^:const latest-version 2)

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

              {:name           "unsuspend"
               :uri            "unsuspend"
               :description    "unsuspend the nuvlabox"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           utils/action-heartbeat
               :uri            utils/action-heartbeat
               :description    "allow to receive heartbeat from nuvlabox"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}])


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
  [{{:keys [version refresh-interval heartbeat-interval owner]
     :or   {version            latest-version
            refresh-interval   utils/default-refresh-interval
            heartbeat-interval utils/default-heartbeat-interval}
     :as   body} :body :as request}]
  (let [is-admin?    (-> request
                         utils/throw-when-payment-required
                         utils/throw-refresh-interval-should-be-bigger
                         utils/throw-heartbeat-interval-should-be-bigger
                         utils/throw-vpn-server-id-should-be-vpn
                         a/is-admin-request?)
        nb-owner     (if is-admin? (or owner "group/nuvla-admin")
                                   (auth/current-active-claim request))
        new-nuvlabox (assoc body :version version
                                 :state utils/state-new
                                 :refresh-interval refresh-interval
                                 :heartbeat-interval heartbeat-interval
                                 :owner nb-owner)
        resp         (add-impl (assoc request :body new-nuvlabox))]
    (ka-crud/publish-on-add resource-type resp)
    resp))


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

        (let [swarm-id (wf-utils/update-coe-service id name acl infrastructure-service-group "swarm"
                                                    :capabilities capabilities)]
          (wf-utils/update-coe-cred id name acl swarm-id nil nil nil "infrastructure-service-swarm")
          (wf-utils/update-swarm-token id name acl swarm-id "MANAGER" nil)
          (wf-utils/update-swarm-token id name acl swarm-id "WORKER" nil))

        (let [k8s-id (wf-utils/update-coe-service id name acl infrastructure-service-group "kubernetes"
                                                  :capabilities capabilities)]
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
  [{:keys [name description location tags ssh-keys capabilities acl
           refresh-interval heartbeat-interval] :as _body}
   {:keys [id owner vpn-server-id] :as existing-resource}]
  (cond-> (dissoc existing-resource :name :description :location :tags :ssh-keys :capabilities :acl)
          name (assoc :name name)
          description (assoc :description description)
          location (assoc :location location)
          tags (assoc :tags tags)
          ssh-keys (assoc :ssh-keys ssh-keys)
          capabilities (assoc :capabilities capabilities)
          refresh-interval (assoc :refresh-interval refresh-interval)
          heartbeat-interval (assoc :heartbeat-interval heartbeat-interval)
          acl (assoc
                :acl (-> acl
                         (select-keys [:view-meta :edit-data :edit-meta :delete])
                         (merge
                           {:owners    ["group/nuvla-admin"]
                            :edit-acl  (vec (distinct (concat (:edit-acl acl) [owner])))
                            :view-acl  (vec (distinct (concat (:view-acl acl) (when vpn-server-id
                                                                                [vpn-server-id]))))
                            :view-data (vec (distinct (concat (:view-data acl) [id])))
                            :manage    (vec (distinct (concat (:manage acl) [id])))})
                         (acl-utils/normalize-acl)))))

(defn restrict-request-body
  [request resource]
  (if (a/is-admin-request? request)
    request
    (update request :body restricted-body resource)))

(defmethod crud/edit resource-type
  [{{uuid :uuid} :params :as request}]
  (let [current (-> (str resource-type "/" uuid)
                    (db/retrieve (assoc-in request [:cimi-params :select] nil))
                    (a/throw-cannot-edit request))
        resp    (-> request
                    utils/throw-refresh-interval-should-be-bigger
                    utils/throw-heartbeat-interval-should-be-bigger
                    utils/throw-vpn-server-id-should-be-vpn
                    (restrict-request-body current)
                    (utils/set-online-request current)
                    edit-impl)]
    (ka-crud/publish-on-edit resource-type resp)
    (edit-subresources current (:body resp))
    resp))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)]
    (try
      (-> (db/retrieve id request)
          (a/throw-cannot-delete request)
          (u/throw-can-not-do-action utils/can-delete? "delete"))
      (let [resp (delete-impl request)]
        (ka-crud/publish-tombstone resource-type id)
        resp)
      (catch Exception e
        (or (ex-data e) (throw e))))))

(def validate-edit-tags-body (u/create-spec-validation-request-body-fn
                               ::common-body/bulk-edit-tags-body))

(defn bulk-edit-tags
  [request bulk-impl]
  (-> request
      validate-edit-tags-body
      bulk-impl))

(def bulk-edit-impl (std-crud/bulk-edit-fn resource-type collection-acl))

(defmethod crud/bulk-action [resource-type "set-tags"]
  [request]
  (bulk-edit-tags request bulk-edit-impl))

(def bulk-add-impl (std-crud/bulk-edit-fn resource-type collection-acl :add))

(defmethod crud/bulk-action [resource-type "add-tags"]
  [request]
  (bulk-edit-tags request bulk-add-impl))

(def bulk-remove-impl (std-crud/bulk-edit-fn resource-type collection-acl :remove))

(defmethod crud/bulk-action [resource-type "remove-tags"]
  [request]
  (bulk-edit-tags request bulk-remove-impl))


;;
;; Activate operation
;;

(defn activate
  [{:keys [id] :as nuvlabox}]
  (u/throw-can-not-do-action
    nuvlabox utils/can-activate? "activate")
  (log/warn "activating nuvlabox:" id)
  (-> nuvlabox
      (assoc :state utils/state-activated)
      wf-utils/create-nuvlabox-status
      wf-utils/create-infrastructure-service-group))


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
      (let [capabilities (some-> body :capabilities set vec)
            ssh-keys     (some-> body :ssh-keys set vec)
            nuvlabox     (-> (db/retrieve id request)
                             (a/throw-cannot-manage request)
                             (u/throw-can-not-do-action
                               utils/can-commission? "commission")
                             (assoc :state utils/state-commissioned)
                             (cond-> capabilities (assoc :capabilities capabilities)
                                     ssh-keys (assoc :ssh-keys ssh-keys))
                             u/update-timestamps
                             crud/validate)]
        (commission nuvlabox request)

        (let [resp (db/edit nuvlabox request)]
          (ka-crud/publish-on-edit resource-type resp))

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
        (assoc :state utils/state-decommissioning
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
          (u/throw-can-not-do-action
            utils/can-decommission? "decommission")
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
  [{:keys [id acl] :as _nuvlabox}]
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


(defmethod crud/do-action [resource-type "check-api"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action
            utils/can-check-api? "check-api")
          (check-api)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Reboot action
;;

(defn reboot
  [{:keys [id acl] :as nuvlabox}]
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


(defmethod crud/do-action [resource-type "reboot"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action utils/can-reboot? "reboot")
          (reboot)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Cluster action
;;


(defn cluster-nuvlabox
  [{:keys [id acl] :as nuvlabox}
   cluster-action nuvlabox-manager-status token advertise-addr]
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


(defmethod crud/do-action [resource-type "cluster-nuvlabox"]
  [{{uuid :uuid} :params {:keys [cluster-action nuvlabox-manager-status token advertise-addr]} :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (when-not (empty? nuvlabox-manager-status)
        (-> (db/retrieve (:id nuvlabox-manager-status) request)
            (a/throw-cannot-view request)))
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action
            utils/can-cluster-nuvlabox? "cluster-nuvlabox")
          (cluster-nuvlabox cluster-action nuvlabox-manager-status token advertise-addr)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Add ssh-key action
;;


(defn add-ssh-key
  [{:keys [id acl] :as nuvlabox} ssh-credential]
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


(defmethod crud/do-action [resource-type "add-ssh-key"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id          (str resource-type "/" uuid)
          ssh-cred-id (:credential body)
          nuvlabox    (-> (db/retrieve id request)
                          (a/throw-cannot-manage request)
                          (u/throw-can-not-do-action
                            utils/can-add-ssh-key? "add-ssh-key"))
          acl         (:acl nuvlabox)
          credential  (if ssh-cred-id
                        (db/retrieve ssh-cred-id request)
                        (wf-utils/create-ssh-key
                          {:acl      acl
                           :template {:href "credential-template/generate-ssh-key"}}))]

      (-> (db/retrieve (:id credential) request)
          (a/throw-cannot-view request))
      (add-ssh-key nuvlabox credential))
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
  [{:keys [id acl] :as nuvlabox} ssh-credential-id]
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
          (or (ex-data e) (throw e)))))))


(defmethod crud/do-action [resource-type "revoke-ssh-key"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id          (str resource-type "/" uuid)
          ssh-cred-id (:credential body)]
      (-> (db/retrieve ssh-cred-id request)
          (a/throw-cannot-view request))
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action
            utils/can-revoke-ssh-key? "revoke-ssh-key")
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
  [{:keys [id acl] :as nuvlabox} nb-release-id payload]
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
          (or (ex-data e) (throw e)))))))


(defmethod crud/do-action [resource-type "update-nuvlabox"]
  [{{uuid :uuid} :params {:keys [nuvlabox-release payload]} :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve nuvlabox-release request)
          (a/throw-cannot-view request))
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action
            utils/can-update-nuvlabox? "update-nuvlabox")
          (update-nuvlabox nuvlabox-release payload)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Assemble NuvlaBox playbooks and prepare them for execution on the edge device
;;


(defn assemble-playbooks
  [{:keys [id] :as _nuvlabox}]
  (try
    (log/warn "Assembling playbooks for execution, for NuvlaBox " id)
    (let [emergency-playbooks (seq (utils/get-playbooks id "EMERGENCY"))]
      (when emergency-playbooks
        (log/warn "Running one-off emergency playbooks for NuvlaBox " id " and disabling them")
        (doseq [{playbook-id :id} emergency-playbooks]
          (crud/edit {:params      {:uuid          (u/id->uuid playbook-id)
                                    :resource-name "nuvlabox-playbook"}
                      :body        {:enabled false}
                      :nuvla/authn auth/internal-identity})))
      (r/text-response (utils/wrap-and-pipe-playbooks (or emergency-playbooks
                                                          (utils/get-playbooks id)))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "assemble-playbooks"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action
            utils/can-assemble-playbooks? "assemble-playbooks")
          (assemble-playbooks)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Enables the nuvlabox-playbooks and provides the mechanism for host-level management
;;


(defn enable-host-level-management
  [{:keys [id host-level-management-api-key] :as nuvlabox} {:keys [base-uri] :as request}]
  (if host-level-management-api-key
    (logu/log-and-throw-400 (str "host level management is already enabled for NuvlaBox " id))
    (try
      (let [[_ credential] (wf-utils/create-nuvlabox-api-key nuvlabox "[nuvlabox-playbook]")
            updated_nuvlabox (assoc nuvlabox :host-level-management-api-key (:api-key credential))]
        (db/edit updated_nuvlabox request)

        (r/json-response {:cronjob (utils/compose-cronjob credential id base-uri)}))
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type "enable-host-level-management"]
  [{{uuid :uuid} :params :as request}]
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
  [{:keys [id host-level-management-api-key] :as _nuvlabox}]
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


(defmethod crud/do-action [resource-type "disable-host-level-management"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action utils/can-disable-host-level-management?
                                     "disable-host-level-management")
          (disable-host-level-management)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Enable the emergency playbooks so that the next host management cycle can do a one-off disaster recovery
;;


(defn enable-emergency-playbooks
  [{:keys [id] :as _nuvlabox} emergency-playbooks-ids current-authn]
  (try
    (log/warn "Enabling emergency playbooks for one-off execution, for NuvlaBox " id)
    (doseq [playbook-id emergency-playbooks-ids]
      (crud/edit {:params      {:uuid          (u/id->uuid playbook-id)
                                :resource-name "nuvlabox-playbook"}
                  :body        {:enabled true}
                  :nuvla/authn current-authn}))
    (r/json-response {:enable-emergency-playbooks emergency-playbooks-ids})
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "enable-emergency-playbooks"]
  [{{uuid :uuid} :params {:keys [emergency-playbooks-ids]} :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action utils/can-enable-emergency-playbooks?
                                     "enable-emergency-playbooks")
          (enable-emergency-playbooks emergency-playbooks-ids (auth/current-authentication request))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Create the NuvlaBox Log resource
;;

(defn create-log
  [{:keys [id] :as _nuvlabox} {:keys [body] :as request}]
  (let [opts       (select-keys body [:since :lines])
        components (:components body)
        session-id (auth/current-session-id request)
        log-acl    {:owners   [id]
                    :view-acl [session-id]
                    :manage   [session-id]
                    :delete   [session-id]}]
    (resource-log/create-log id components log-acl opts)))


(defmethod crud/do-action [resource-type "create-log"]
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)]
    (-> (db/retrieve id request)
        (a/throw-cannot-manage request)
        (u/throw-can-not-do-action utils/can-create-log? "create-log")
        (create-log request))))

;;
;; Allows for the creation of a NuvlaBox API key on-demand
;;

(defn create-new-api-key
  [{:keys [id] :as nuvlabox}]
  (log/warn "generating new API key for nuvlabox:" id)
  (-> (wf-utils/create-nuvlabox-api-key nuvlabox "")
      second
      (r/json-response)))


(defmethod crud/do-action [resource-type "generate-new-api-key"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action utils/can-generate-new-api-key?
                                     "generate-new-api-key")
          (create-new-api-key)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "unsuspend"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (utils/throw-when-payment-required request)
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (a/throw-cannot-edit request)
          (u/throw-can-not-do-action utils/can-unsuspend? "unsuspend"))
      (crud/edit-by-id-as-admin id {:state utils/state-commissioned}))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type utils/action-heartbeat]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action utils/can-heartbeat? utils/action-heartbeat)
          utils/set-default-heartbeat-interval
          utils/update-last-heartbeat
          utils/update-next-heartbeat
          ;u/update-timestamps
          (utils/set-online-resource true)
          (db/edit request))
      (r/json-response {:jobs (utils/get-jobs id)}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Set operation
;;

;;
;; operations for states for owner are:
;;
;;                edit delete activate commission decommission unsuspend heartbeat
;; NEW             Y     Y       Y
;; ACTIVATED       Y                       Y           Y                     Y
;; COMMISSIONED    Y                       Y           Y                     Y
;; DECOMMISSIONING Y                                   Y
;; DECOMMISSIONED  Y     Y
;; ERROR           Y     Y                             Y
;; SUSPENDED       Y                                   Y          Y

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [edit-op              (u/operation-map id :edit)
        delete-op            (u/operation-map id :delete)
        activate-op          (u/action-map id :activate)
        commission-op        (u/action-map id :commission)
        decommission-op      (u/action-map id :decommission)
        check-api-op         (u/action-map id :check-api)
        reboot-op            (u/action-map id :reboot)
        add-ssh-key-op       (u/action-map id :add-ssh-key)
        revoke-ssh-key-op    (u/action-map id :revoke-ssh-key)
        update-nuvlabox-op   (u/action-map id :update-nuvlabox)
        cluster-nb-op        (u/action-map id :cluster-nuvlabox)
        assemble-pb-op       (u/action-map id :assemble-playbooks)
        enable-host-mgmt-op  (u/action-map id :enable-host-level-management)
        disable-host-mgmt-op (u/action-map id :disable-host-level-management)
        enable-emergency-op  (u/action-map id :enable-emergency-playbooks)
        create-log-op        (u/action-map id :create-log)
        generate-new-key-op  (u/action-map id :generate-new-api-key)
        unsuspend-op         (u/action-map id :unsuspend)
        heartbeat-op         (u/action-map id utils/action-heartbeat)
        can-manage?          (a/can-manage? resource request)]
    (assoc resource
      :operations
      (cond-> []
              (a/can-edit? resource request) (conj edit-op)
              (and (a/can-delete? resource request)
                   (utils/can-delete? resource)) (conj delete-op)
              can-manage?
              (cond-> (utils/can-activate? resource) (conj activate-op)
                      (utils/can-commission? resource) (conj commission-op)
                      (utils/can-decommission? resource) (conj decommission-op)
                      (utils/can-check-api? resource) (conj check-api-op)
                      (utils/can-add-ssh-key? resource) (conj add-ssh-key-op)
                      (utils/can-revoke-ssh-key? resource) (conj revoke-ssh-key-op)
                      (utils/can-update-nuvlabox? resource) (conj update-nuvlabox-op)
                      (utils/can-cluster-nuvlabox? resource) (conj cluster-nb-op)
                      (utils/can-reboot? resource) (conj reboot-op)
                      (utils/can-assemble-playbooks? resource) (conj assemble-pb-op)
                      (utils/can-enable-emergency-playbooks? resource) (conj enable-emergency-op)
                      (utils/can-enable-host-level-management? resource) (conj enable-host-mgmt-op)
                      (utils/can-disable-host-level-management? resource) (conj disable-host-mgmt-op)
                      (utils/can-create-log? resource) (conj create-log-op)
                      (utils/can-generate-new-api-key? resource) (conj generate-new-key-op)
                      (utils/can-unsuspend? resource) (conj unsuspend-op)
                      (utils/can-heartbeat? resource) (conj heartbeat-op)
                      )))))

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nuvlabox/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nuvlabox/schema)
  (md/register resource-metadata))
