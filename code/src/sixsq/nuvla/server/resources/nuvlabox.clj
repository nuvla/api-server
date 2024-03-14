(ns sixsq.nuvla.server.resources.nuvlabox
  "
The core `nuvlabox` resource that contains only those attributes required in
all subtypes of this resource. Versioned subclasses define the attributes for a
particular NuvlaBox release.
"
  (:require
    [clojure.data.json :as json]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.job.interface :as job-interface]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.nuvlabox.status-utils :as status-utils]
    [sixsq.nuvla.server.resources.nuvlabox.workflow-utils :as wf-utils]
    [sixsq.nuvla.server.resources.resource-log :as resource-log]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.common-body :as common-body]
    [sixsq.nuvla.server.resources.spec.nuvlabox :as nuvlabox]
    [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as time]))


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
               :output-message "application/json"}

              {:name           utils/action-set-offline
               :uri            utils/action-set-offline
               :description    "allow to job executor as admin to set nuvlabox as offline"
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
                         (utils/throw-refresh-interval-should-be-bigger nil)
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


(def edit-impl (std-crud/edit-fn resource-type
                                 :immutable-keys [:online]
                                 :options {:refresh false}))

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
                    crud/retrieve-by-id-as-admin
                    (a/throw-cannot-edit request))
        resp    (-> request
                    (utils/throw-refresh-interval-should-be-bigger current)
                    utils/throw-heartbeat-interval-should-be-bigger
                    utils/throw-vpn-server-id-should-be-vpn
                    (restrict-request-body current)
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
      (-> (crud/retrieve-by-id-as-admin id)
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
  [{{uuid :uuid} :params :as _request}]
  (try
    (let [id (str resource-type "/" uuid)
          [nuvlabox-activated api-secret-info] (-> (crud/retrieve-by-id-as-admin id)
                                                   activate
                                                   u/update-timestamps
                                                   (wf-utils/create-nuvlabox-api-key ""))]

      (db/edit nuvlabox-activated)

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
            nuvlabox     (-> (crud/retrieve-by-id-as-admin id)
                             (a/throw-cannot-manage request)
                             (u/throw-can-not-do-action
                               utils/can-commission? "commission")
                             (assoc :state utils/state-commissioned)
                             (cond-> capabilities (assoc :capabilities capabilities)
                                     ssh-keys (assoc :ssh-keys ssh-keys))
                             u/update-timestamps
                             crud/validate)]
        (commission nuvlabox request)

        (let [resp (db/edit nuvlabox)]
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
        db/edit)

    ;; read back the updated resource to ensure that ACL is fully normalized
    (crud/retrieve-by-id-as-admin id)))


(defmulti decommission-async
          "Creates a job to handle all the asynchronous cleanup that is
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
      (-> (crud/retrieve-by-id-as-admin id)
          (a/throw-cannot-manage request)
          (u/throw-can-not-do-action
            utils/can-decommission? "decommission")
          (decommission-sync request)
          (decommission-async request))
      (catch Exception e
        (or (ex-data e) (throw e))))))

(defmethod crud/do-action [resource-type utils/action-heartbeat]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-can-not-do-action utils/can-heartbeat? utils/action-heartbeat)
        (utils/set-online! true)
        (utils/build-response)
        r/json-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type utils/action-set-offline]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-not-admin-request request)
        (u/throw-can-not-do-action
          utils/can-set-offline? utils/action-set-offline)
        (utils/set-online! false))
    (r/map-response "offline" 200)
    (catch Exception e
      (or (ex-data e) (throw e)))))


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
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-can-not-do-action
          utils/can-check-api? "check-api")
        (check-api))
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
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-can-not-do-action utils/can-reboot? "reboot")
        (reboot))
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
        (crud/retrieve-by-id (:id nuvlabox-manager-status) request))
      (-> (crud/retrieve-by-id-as-admin id)
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
          nuvlabox    (-> (crud/retrieve-by-id-as-admin id)
                          (a/throw-cannot-manage request)
                          (u/throw-can-not-do-action
                            utils/can-add-ssh-key? "add-ssh-key"))
          acl         (:acl nuvlabox)
          credential  (if ssh-cred-id
                        (crud/retrieve-by-id-as-admin ssh-cred-id)
                        (wf-utils/create-ssh-key
                          {:acl      acl
                           :template {:href "credential-template/generate-ssh-key"}}))]

      (crud/retrieve-by-id (:id credential) request)
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
      (crud/retrieve-by-id ssh-cred-id request)
      (-> (crud/retrieve-by-id-as-admin id)
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
      (crud/retrieve-by-id nuvlabox-release request)
      (-> (crud/retrieve-by-id-as-admin id)
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
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-can-not-do-action
          utils/can-assemble-playbooks? "assemble-playbooks")
        (assemble-playbooks))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Enables the nuvlabox-playbooks and provides the mechanism for host-level management
;;


(defn enable-host-level-management
  [{:keys [id host-level-management-api-key] :as nuvlabox}
   {:keys [base-uri] :as _request}]
  (if host-level-management-api-key
    (logu/log-and-throw-400 (str "host level management is already enabled for NuvlaBox " id))
    (try
      (let [[_ credential] (wf-utils/create-nuvlabox-api-key nuvlabox "[nuvlabox-playbook]")
            updated_nuvlabox (assoc nuvlabox :host-level-management-api-key (:api-key credential))]
        (db/edit updated_nuvlabox)

        (r/json-response {:cronjob (utils/compose-cronjob credential id base-uri)}))
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type "enable-host-level-management"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (enable-host-level-management request))
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
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-can-not-do-action utils/can-disable-host-level-management?
                                   "disable-host-level-management")
        (disable-host-level-management))
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
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-can-not-do-action utils/can-enable-emergency-playbooks?
                                   "enable-emergency-playbooks")
        (enable-emergency-playbooks emergency-playbooks-ids (auth/current-authentication request)))
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
        log-acl    {:owners    ["group/nuvla-admin"]
                    :edit-data [id]
                    :manage    [id session-id]
                    :view-acl  [session-id]
                    :delete    [session-id]}]
    (resource-log/create-log id components log-acl opts)))


(defmethod crud/do-action [resource-type "create-log"]
  [{{uuid :uuid} :params :as request}]
  (-> (str resource-type "/" uuid)
      crud/retrieve-by-id-as-admin
      (a/throw-cannot-manage request)
      (u/throw-can-not-do-action utils/can-create-log? "create-log")
      (create-log request)))

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
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-can-not-do-action utils/can-generate-new-api-key?
                                   "generate-new-api-key")
        (create-new-api-key))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "unsuspend"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (utils/throw-when-payment-required request)
      (-> (crud/retrieve-by-id-as-admin id)
          (a/throw-cannot-manage request)
          (a/throw-cannot-edit request)
          (u/throw-can-not-do-action utils/can-unsuspend? "unsuspend"))
      (crud/edit-by-id-as-admin id {:state utils/state-commissioned}))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defn compute-bucket-availability
  "Compute bucket availability based on sum online and seconds in bucket."
  [sum-time-online seconds-in-bucket]
  (when (and sum-time-online (some-> seconds-in-bucket pos?))
    (let [avg (double (/ sum-time-online seconds-in-bucket))]
      (if (> avg 1.0) 1.0 avg))))

(defn compute-seconds-in-bucket
  [{start-str :timestamp} granularity now]
  (let [start (time/date-from-str start-str)
        end   (time/plus start (status-utils/granularity->duration granularity))
        end   (if (time/after? end now) now end)]
    (time/time-between start end :seconds)))

(defn edge-at
  "Returns the edge if it was created before the given timestamp, and if sent
   a metric datapoint before the given timestamp, nil otherwise"
  [{:keys [created first-availability-status] :as _nuvlabox} timestamp]
  (and (some-> created
               (time/date-from-str)
               (time/before? timestamp))
       (some-> first-availability-status
               :timestamp
               time/date-from-str
               (time/before? timestamp))))

(defn bucket-end-time
  [backet-start-time granularity]
  (time/plus (time/date-from-str backet-start-time)
             (status-utils/granularity->duration granularity)))

(defn bucket-online-stats
  "Compute online stats for a bucket."
  [{nuvlaedge-id :id :as nuvlabox} {start-str :timestamp} granularity now hits latest-status]
  (let [start           (time/date-from-str start-str)
        end             (time/plus start (status-utils/granularity->duration granularity))
        end             (if (time/after? end now) now end)
        relevant-hits   (filter #(and (= nuvlaedge-id (:nuvlaedge-id %))
                                      (not (time/before? (:timestamp %) start))
                                      (time/before? (:timestamp %) end))
                                hits)
        sum-time-online (reduce
                          (fn [online-secs [{from :timestamp :keys [online]}
                                            {to :timestamp}]]
                            (when (some? online)
                              (cond
                                (edge-at nuvlabox from)
                                (+ (or online-secs 0)
                                   (* online (time/time-between from to :seconds)))
                                (edge-at nuvlabox to)
                                (+ (or online-secs 0)
                                   (* online (time/time-between (time/date-from-str (:created nuvlabox)) to :seconds)))
                                :else
                                nil)))
                          nil
                          (map vector
                               (cons {:timestamp start
                                      :online    latest-status}
                                     relevant-hits)
                               (conj (vec relevant-hits)
                                     {:timestamp end})))]
    [sum-time-online (:online (or (last relevant-hits)
                                  {:online latest-status}))]))

(defn update-resp-ts-data
  [resp f]
  (-> resp
      vec
      (update-in [0 :ts-data] f)))

(defn update-resp-ts-data-points
  [resp f]
  (update-resp-ts-data
    resp
    (fn [ts-data]
      (map f ts-data))))

(defn update-resp-ts-data-point-aggs
  [resp f]
  (update-resp-ts-data-points
    resp
    (fn [ts-data-point]
      (update ts-data-point :aggregations (partial f ts-data-point)))))

(defn commissioned?
  [nuvlabox]
  (= utils/state-commissioned (:state nuvlabox)))

(defn assoc-first-availability-status
  [nuvlabox]
  (assoc nuvlabox :first-availability-status
                  (utils/first-availability-status (:id nuvlabox))))

(defn available-before?
  [{:keys [first-availability-status] :as _nuvlabox} timestamp]
  (some-> first-availability-status :timestamp time/date-from-str (time/before? timestamp)))

(defn filter-commissioned-nuvlaboxes
  [{:keys [to nuvlaboxes] :as base-query-opts}]
  (let [nuvlaboxes (->> nuvlaboxes
                        (filter commissioned?)
                        (map assoc-first-availability-status)
                        (filter #(available-before? % to)))]
    (assoc base-query-opts
      :nuvlaboxes nuvlaboxes
      :nuvlaedge-ids (map :id nuvlaboxes))))

(defn compute-nuvlabox-availability*
  "Compute availability for a single nuvlabox."
  [resp now granularity {nuvlaedge-id :id :as nuvlabox} update-fn]
  (let [hits                 (->> (get-in resp [0 :hits])
                                  (map #(update % :timestamp time/date-from-str))
                                  reverse)
        first-bucket-ts      (some-> resp first :ts-data first :timestamp time/date-from-str)
        prev-status          (some-> nuvlaedge-id
                                     (utils/latest-availability-status first-bucket-ts)
                                     :online)
        update-ts-data-point (fn [[ts-data latest-status] {:keys [timestamp] :as ts-data-point}]
                               (if (edge-at nuvlabox (bucket-end-time timestamp granularity))
                                 (let [seconds-in-bucket (compute-seconds-in-bucket ts-data-point granularity now)
                                       [sum-time-online latest-status] (bucket-online-stats nuvlabox ts-data-point granularity now hits latest-status)]
                                   [(conj ts-data
                                          (update-fn
                                            ts-data-point
                                            (compute-bucket-availability sum-time-online seconds-in-bucket)))
                                    latest-status])
                                 [(conj ts-data ts-data-point) latest-status]))]
    (update-resp-ts-data
      resp
      (fn [ts-data]
        (first (reduce update-ts-data-point
                       [[] prev-status]
                       ts-data))))))

(defn compute-nuvlabox-availability
  [[{:keys [predefined-aggregations granularity nuvlaboxes] :as query-opts} resp]]
  (if predefined-aggregations
    (let [nuvlabox     (first nuvlaboxes)
          now          (time/now)
          update-av-fn (fn [ts-data-point availability]
                         (update ts-data-point
                                 :aggregations
                                 (fn [aggs]
                                   (assoc-in aggs [:avg-online :value] availability))))]
      [query-opts (compute-nuvlabox-availability* resp now granularity nuvlabox update-av-fn)])
    [query-opts resp]))

(defn dissoc-hits
  [[query-opts resp]]
  [query-opts (update-in resp [0] dissoc :hits)])

(defn csv-export-fn
  [dimension-keys-fn meta-keys-fn metric-keys-fn data-fn]
  (fn [{:keys [resps] :as options}]
    (utils/metrics-data->csv
      options
      (dimension-keys-fn options)
      (meta-keys-fn options)
      (metric-keys-fn options)
      data-fn
      (first resps))))

(defn csv-dimension-keys-fn
  []
  (fn [{:keys [raw predefined-aggregations datasets datasets-opts mode]}]
    (if raw
      []
      (let [{group-by-field :group-by} (get datasets-opts (first datasets))
            dimension-keys (case mode
                             :single-edge-query
                             []
                             :multi-edge-query
                             [:nuvlaedge-count])]
        (cond-> dimension-keys
                (and predefined-aggregations group-by-field) (conj group-by-field))))))

(defn csv-meta-keys-fn
  []
  (fn [{:keys [mode raw]}]
    (if raw
      (case mode
        :single-edge-query
        [:timestamp]
        :multi-edge-query
        [:timestamp :nuvlaedge-id])
      [:timestamp :doc-count])))

(defn availability-csv-metric-keys-fn
  []
  (fn [{:keys [mode raw datasets datasets-opts]}]
    (let [{:keys [response-aggs]}
          (get datasets-opts (first datasets))]
      (if raw
        [:online]
        response-aggs))))

(defn availability-csv-data-fn
  []
  (fn [{:keys [raw]} {:keys [aggregations] :as data-point} metric-key]
    (if raw
      (get data-point metric-key)
      (get-in aggregations [metric-key :value]))))

(defn availability-csv-export-fn
  []
  (csv-export-fn (csv-dimension-keys-fn)
                 (csv-meta-keys-fn)
                 (availability-csv-metric-keys-fn)
                 (availability-csv-data-fn)))

(defn telemetry-csv-metric-keys-fn
  [metric]
  (fn [{:keys [raw datasets datasets-opts resps]}]
    (let [{:keys [aggregations response-aggs]}
          (get datasets-opts (first datasets))]
      (if raw
        (sort (keys (-> resps ffirst :ts-data first (get metric))))
        (or response-aggs (keys aggregations))))))

(defn telemetry-csv-data-fn
  [metric]
  (fn [{:keys [raw]} {:keys [aggregations] :as data-point} metric-key]
    (if raw
      (get-in data-point [metric metric-key])
      (get-in aggregations [metric-key :value]))))

(defn telemetry-csv-export-fn
  [metric]
  (csv-export-fn (csv-dimension-keys-fn)
                 (csv-meta-keys-fn)
                 (telemetry-csv-metric-keys-fn metric)
                 (telemetry-csv-data-fn metric)))

(defn single-edge-datasets
  []
  {"availability-stats"      {:metric          "availability"
                              :pre-process-fn  filter-commissioned-nuvlaboxes
                              :post-process-fn (comp dissoc-hits
                                                     compute-nuvlabox-availability)
                              :response-aggs   [:avg-online]
                              :csv-export-fn   (availability-csv-export-fn)}
   "cpu-stats"               {:metric        "cpu"
                              :aggregations  {:avg-cpu-capacity    {:avg {:field :cpu.capacity}}
                                              :avg-cpu-load        {:avg {:field :cpu.load}}
                                              :avg-cpu-load-1      {:avg {:field :cpu.load-1}}
                                              :avg-cpu-load-5      {:avg {:field :cpu.load-5}}
                                              :context-switches    {:max {:field :cpu.context-switches}}
                                              :interrupts          {:max {:field :cpu.interrupts}}
                                              :software-interrupts {:max {:field :cpu.software-interrupts}}
                                              :system-calls        {:max {:field :cpu.system-calls}}}
                              :csv-export-fn (telemetry-csv-export-fn :cpu)}
   "ram-stats"               {:metric        "ram"
                              :aggregations  {:avg-ram-capacity {:avg {:field :ram.capacity}}
                                              :avg-ram-used     {:avg {:field :ram.used}}}
                              :csv-export-fn (telemetry-csv-export-fn :ram)}
   "disk-stats"              {:metric        "disk"
                              :group-by      :disk.device
                              :aggregations  {:avg-disk-capacity {:avg {:field :disk.capacity}}
                                              :avg-disk-used     {:avg {:field :disk.used}}}
                              :csv-export-fn (telemetry-csv-export-fn :disk)}
   "network-stats"           {:metric        "network"
                              :group-by      :network.interface
                              :aggregations  {:bytes-received    {:max {:field :network.bytes-received}}
                                              :bytes-transmitted {:max {:field :network.bytes-transmitted}}}
                              :csv-export-fn (telemetry-csv-export-fn :network)}
   "power-consumption-stats" {:metric        "power-consumption"
                              :group-by      :power-consumption.metric-name
                              :aggregations  {:energy-consumption {:max {:field :power-consumption.energy-consumption}}
                                              #_:unit                   #_{:first {:field :power-consumption.unit}}}
                              :csv-export-fn (telemetry-csv-export-fn :power-consumption)}})

(defn edges-at
  "Returns the edges which were created before the given timestamp"
  [nuvlaboxes timestamp]
  (filter #(edge-at % timestamp) nuvlaboxes))

(defn expected-bucket-edge-ids
  [nuvlaboxes granularity {:keys [timestamp] :as _ts-data-point}]
  (->> (bucket-end-time timestamp granularity)
       (edges-at nuvlaboxes)
       (map :id)))

(defn init-edge-buckets
  [resp nuvlaboxes granularity]
  (update-resp-ts-data-points
    resp
    (fn [ts-data-point]
      (assoc-in ts-data-point [:aggregations :by-edge :buckets]
                (mapv (fn [ne-id] {:key ne-id})
                      (expected-bucket-edge-ids nuvlaboxes granularity ts-data-point))))))

(defn compute-nuvlaboxes-availabilities
  [[{:keys [predefined-aggregations granularity nuvlaboxes] :as query-opts} resp]]
  (if predefined-aggregations
    (let [now (time/now)]
      [query-opts
       (reduce
         (fn [resp nuvlabox]
           (let [edge-bucket-update-fn
                 (fn [ts-data-point availability]
                   (let [idx (->> (get-in ts-data-point [:aggregations :by-edge :buckets])
                                  (keep-indexed #(when (= (:key %2) (:id nuvlabox)) %1))
                                  first)]
                     (cond-> ts-data-point
                             idx (update-in [:aggregations :by-edge :buckets idx]
                                            (fn [aggs]
                                              (assoc-in aggs [:edge-avg-online :value]
                                                        availability))))))]
             (compute-nuvlabox-availability* resp now granularity nuvlabox
                                             edge-bucket-update-fn)))
         (init-edge-buckets resp nuvlaboxes granularity)
         nuvlaboxes)])
    [query-opts resp]))

(defn compute-global-availability
  [[{:keys [predefined-aggregations] :as query-opts} resp]]
  [query-opts
   (cond->
     resp
     predefined-aggregations
     (update-resp-ts-data-point-aggs
       (fn [_ts-data-point {:keys [by-edge] :as aggs}]
         (let [avgs-count  (count (:buckets by-edge))
               avgs-online (keep #(-> % :edge-avg-online :value)
                                 (:buckets by-edge))]
           ;; here we can compute the average of the averages, because we give the same weight
           ;; to each edge (caveat: an edge created in the middle of a bucket will have the same
           ;; weight then an edge that was there since the beginning of the bucket).
           (assoc aggs :global-avg-online
                       {:value (if (seq avgs-online)
                                 (double (/ (apply + avgs-online)
                                            avgs-count))
                                 nil)})))))])

(defn add-edges-count
  [[{:keys [predefined-aggregations] :as query-opts} resp]]
  [query-opts
   (cond->
     resp
     predefined-aggregations
     (update-resp-ts-data-point-aggs
       (fn [_ts-data-point {:keys [by-edge] :as aggs}]
         (assoc aggs :edges-count {:value (count (:buckets by-edge))}))))])

(defn add-virtual-edge-number-by-status-fn
  [[{:keys [predefined-aggregations granularity nuvlaboxes] :as query-opts} resp]]
  (if predefined-aggregations
    (let [edges-count (fn [timestamp] (count (edges-at nuvlaboxes (bucket-end-time timestamp granularity))))]
      [query-opts
       (update-resp-ts-data-points
         resp
         (fn [{:keys [timestamp aggregations] :as ts-data-point}]
           (let [global-avg-online    (get-in aggregations [:global-avg-online :value])
                 edges-count-agg      (get-in aggregations [:edges-count :value])
                 n-virt-online-edges  (double (or (some->> global-avg-online (* edges-count-agg)) 0))
                 n-edges              (edges-count timestamp)
                 n-virt-offline-edges (- n-edges n-virt-online-edges)]
             (-> ts-data-point
                 (assoc-in [:aggregations :virtual-edges-online]
                           {:value n-virt-online-edges})
                 (assoc-in [:aggregations :virtual-edges-offline]
                           {:value n-virt-offline-edges})))))])
    [query-opts resp]))

(defn update-resp-edge-buckets
  [resp f]
  (update-resp-ts-data-point-aggs
    resp
    (fn [ts-data-point aggs]
      (update-in aggs [:by-edge :buckets]
                 (partial map (partial f ts-data-point))))))

(defn add-edge-names-fn
  [[{:keys [predefined-aggregations nuvlaboxes] :as query-opts} resp]]
  (if predefined-aggregations
    (let [edge-names-by-id (->> nuvlaboxes
                                (map (fn [{:keys [id name]}]
                                       [id name]))
                                (into {}))]
      [query-opts
       (update-resp-edge-buckets
         resp
         (fn [_ts-data-point {edge-id :key :as bucket}]
           (assoc bucket :name (get edge-names-by-id edge-id))))])
    [query-opts resp]))

(defn add-missing-edges-fn
  [[{:keys [predefined-aggregations granularity nuvlaboxes] :as query-opts} resp]]
  (if predefined-aggregations
    (letfn [(update-buckets
              [ts-data-point buckets]
              (let [bucket-edge-ids  (set (map :key buckets))
                    missing-edge-ids (set/difference (set (expected-bucket-edge-ids nuvlaboxes granularity ts-data-point))
                                                     bucket-edge-ids)]
                (concat buckets
                        (map (fn [missing-edge-id]
                               {:key       missing-edge-id
                                :doc_count 0})
                             missing-edge-ids))))]
      [query-opts
       (update-resp-ts-data-points
         resp
         (fn [ts-data-point]
           (update-in ts-data-point [:aggregations :by-edge :buckets]
                      (partial update-buckets ts-data-point))))])
    [query-opts resp]))

(defn keep-response-aggs-only
  [{:keys [predefined-aggregations response-aggs] :as _query-opts} resp]
  (cond->
    resp
    predefined-aggregations
    (update-resp-ts-data-point-aggs
      (fn [_ts-data-point aggs]
        (if response-aggs
          (select-keys aggs response-aggs)
          aggs)))))

(defn multi-edge-datasets
  []
  (let [group-by-field     (fn [field aggs]
                             {:terms        {:field field}
                              :aggregations aggs})
        group-by-edge      (fn [aggs] (group-by-field :nuvlaedge-id aggs))
        group-by-device    (fn [aggs] (group-by-field :disk.device aggs))
        group-by-interface (fn [aggs] (group-by-field :network.interface aggs))]
    {"availability-stats"      {:metric          "availability"
                                :pre-process-fn  filter-commissioned-nuvlaboxes
                                :post-process-fn (comp add-virtual-edge-number-by-status-fn
                                                       dissoc-hits
                                                       compute-global-availability
                                                       add-edges-count
                                                       add-missing-edges-fn
                                                       compute-nuvlaboxes-availabilities)
                                :response-aggs   [:edges-count
                                                  :virtual-edges-online
                                                  :virtual-edges-offline]
                                :csv-export-fn   (availability-csv-export-fn)}
     "availability-by-edge"    {:metric          "availability"
                                :pre-process-fn  filter-commissioned-nuvlaboxes
                                :post-process-fn (comp dissoc-hits
                                                       compute-global-availability
                                                       add-edges-count
                                                       add-edge-names-fn
                                                       add-missing-edges-fn
                                                       compute-nuvlaboxes-availabilities)
                                :response-aggs   [:edges-count
                                                  :by-edge
                                                  :global-avg-online]}
     "cpu-stats"               {:metric        "cpu"
                                :aggregations  {:avg-cpu-capacity        (group-by-edge {:by-edge {:avg {:field :cpu.capacity}}})
                                                :avg-cpu-load            (group-by-edge {:by-edge {:avg {:field :cpu.load}}})
                                                :avg-cpu-load-1          (group-by-edge {:by-edge {:avg {:field :cpu.load-1}}})
                                                :avg-cpu-load-5          (group-by-edge {:by-edge {:avg {:field :cpu.load-5}}})
                                                :context-switches        (group-by-edge {:by-edge {:max {:field :cpu.context-switches}}})
                                                :interrupts              (group-by-edge {:by-edge {:max {:field :cpu.interrupts}}})
                                                :software-interrupts     (group-by-edge {:by-edge {:max {:field :cpu.software-interrupts}}})
                                                :system-calls            (group-by-edge {:by-edge {:max {:field :cpu.system-calls}}})
                                                :sum-avg-cpu-capacity    {:sum_bucket {:buckets_path :avg-cpu-capacity>by-edge}}
                                                :sum-avg-cpu-load        {:sum_bucket {:buckets_path :avg-cpu-load>by-edge}}
                                                :sum-avg-cpu-load-1      {:sum_bucket {:buckets_path :avg-cpu-load-1>by-edge}}
                                                :sum-avg-cpu-load-5      {:sum_bucket {:buckets_path :avg-cpu-load-5>by-edge}}
                                                :sum-context-switches    {:sum_bucket {:buckets_path :context-switches>by-edge}}
                                                :sum-interrupts          {:sum_bucket {:buckets_path :interrupts>by-edge}}
                                                :sum-software-interrupts {:sum_bucket {:buckets_path :software-interrupts>by-edge}}
                                                :sum-system-calls        {:sum_bucket {:buckets_path :system-calls>by-edge}}}
                                :response-aggs [:sum-avg-cpu-capacity :sum-avg-cpu-load :sum-avg-cpu-load-1 :sum-avg-cpu-load-5
                                                :sum-context-switches :sum-interrupts :sum-software-interrupts :sum-system-calls]
                                :csv-export-fn (telemetry-csv-export-fn :cpu)}
     "ram-stats"               {:metric        "ram"
                                :aggregations  {:avg-ram-capacity     (group-by-edge {:by-edge {:avg {:field :ram.capacity}}})
                                                :avg-ram-used         (group-by-edge {:by-edge {:avg {:field :ram.used}}})
                                                :sum-avg-ram-capacity {:sum_bucket {:buckets_path :avg-ram-capacity>by-edge}}
                                                :sum-avg-ram-used     {:sum_bucket {:buckets_path :avg-ram-used>by-edge}}}
                                :response-aggs [:sum-avg-ram-capacity :sum-avg-ram-used]
                                :csv-export-fn (telemetry-csv-export-fn :ram)}
     "disk-stats"              {:metric        "disk"
                                :aggregations  {:avg-disk-capacity     (group-by-edge
                                                                         {:by-edge                 (group-by-device
                                                                                                     {:by-device {:avg {:field :disk.capacity}}})
                                                                          :total-avg-edge-capacity {:sum_bucket {:buckets_path :by-edge>by-device}}})
                                                :avg-disk-used         (group-by-edge
                                                                         {:by-edge                      (group-by-device
                                                                                                          {:by-device {:avg {:field :disk.used}}})
                                                                          :total-avg-edge-used-capacity {:sum_bucket {:buckets_path :by-edge>by-device}}})
                                                :sum-avg-disk-capacity {:sum_bucket {:buckets_path :avg-disk-capacity>total-avg-edge-capacity}}
                                                :sum-avg-disk-used     {:sum_bucket {:buckets_path :avg-disk-used>total-avg-edge-used-capacity}}}
                                :response-aggs [:sum-avg-disk-capacity :sum-avg-disk-used]
                                :csv-export-fn (telemetry-csv-export-fn :disk)}
     "network-stats"           {:metric        "network"
                                :aggregations  {:bytes-received        (group-by-edge
                                                                         {:by-edge                   (group-by-interface
                                                                                                       {:by-interface {:max {:field :network.bytes-received}}})
                                                                          :total-edge-bytes-received {:sum_bucket {:buckets_path :by-edge>by-interface}}})
                                                :bytes-transmitted     (group-by-edge
                                                                         {:by-edge                      (group-by-interface
                                                                                                          {:by-interface {:max {:field :network.bytes-transmitted}}})
                                                                          :total-edge-bytes-transmitted {:sum_bucket {:buckets_path :by-edge>by-interface}}})
                                                :sum-bytes-received    {:sum_bucket {:buckets_path :bytes-received>total-edge-bytes-received}}
                                                :sum-bytes-transmitted {:sum_bucket {:buckets_path :bytes-transmitted>total-edge-bytes-transmitted}}}
                                :response-aggs [:sum-bytes-received :sum-bytes-transmitted]
                                :csv-export-fn (telemetry-csv-export-fn :network)}
     "power-consumption-stats" {:metric        "power-consumption"
                                :group-by      :power-consumption.metric-name
                                :aggregations  {:energy-consumption     (group-by-edge {:by-edge {:max {:field :power-consumption.energy-consumption}}})
                                                #_:unit                   #_{:first {:field :power-consumption.unit}}
                                                :sum-energy-consumption {:sum_bucket {:buckets_path :energy-consumption>by-edge}}}
                                :response-aggs [:sum-energy-consumption]
                                :csv-export-fn (telemetry-csv-export-fn :power-consumption)}}))

(defn parse-params
  [{:keys [uuid dataset from to granularity custom-es-aggregations] :as params}]
  (let [datasets                (if (coll? dataset) dataset [dataset])
        raw                     (= "raw" granularity)
        predefined-aggregations (not (or raw custom-es-aggregations))
        custom-es-aggregations  (cond-> custom-es-aggregations
                                        (string? custom-es-aggregations)
                                        json/read-str)]
    (-> params
        (assoc :datasets datasets)
        (assoc :from (time/date-from-str from))
        (assoc :to (time/date-from-str to))
        (cond->
          uuid (assoc :id (u/resource-id resource-type uuid))
          raw (assoc :raw true)
          predefined-aggregations (assoc :predefined-aggregations true)
          custom-es-aggregations (assoc :custom-es-aggregations custom-es-aggregations)))))

(defn throw-mandatory-dataset-parameter
  [{:keys [datasets] :as params}]
  (when-not (seq datasets) (logu/log-and-throw-400 "dataset parameter is mandatory"))
  params)

(defn throw-mandatory-from-to-parameters
  [{:keys [from to] :as params}]
  (when-not from
    (logu/log-and-throw-400 (str "from parameter is mandatory, with format " time/iso8601-format)))
  (when-not to
    (logu/log-and-throw-400 (str "to parameter is mandatory, with format " time/iso8601-format)))
  params)

(defn throw-from-not-before-to
  [{:keys [from to] :as params}]
  (when-not (time/before? from to)
    (logu/log-and-throw-400 "from must be before to"))
  params)

(defn throw-mandatory-granularity-parameter
  [{:keys [raw granularity custom-es-aggregations] :as params}]
  (when (and (not raw) (not custom-es-aggregations) (empty? granularity))
    (logu/log-and-throw-400 "granularity parameter is mandatory"))
  params)

(defn throw-custom-es-aggregations-checks
  [{:keys [custom-es-aggregations granularity] :as params}]
  (when custom-es-aggregations
    (when granularity
      (logu/log-and-throw-400 "when custom-es-aggregations is specified, granularity parameter must be omitted")))
  params)

(defn throw-raw-data-with-other-datasets
  [{:keys [raw datasets] :as params}]
  (when (and raw (> (count datasets) 1))
    (logu/log-and-throw-400 "cannot mix raw with other datasets in the same request"))
  params)

(defn throw-too-many-data-points
  [{:keys [from to granularity predefined-aggregations] :as params}]
  (when predefined-aggregations
    (let [max-n-buckets utils/max-data-points
          n-buckets     (.dividedBy (time/duration from to)
                                    (status-utils/granularity->duration granularity))]
      (when (> n-buckets max-n-buckets)
        (logu/log-and-throw-400 "too many data points requested. Please restrict the time interval or increase the time granularity."))))
  params)

(defn throw-response-format-not-supported
  [{:keys [accept-header] :as params}]
  (when (and (some? accept-header) (not (#{"application/json" "text/csv"} accept-header)))
    (logu/log-and-throw-400 (str "format not supported: " accept-header)))
  params)

(defn assoc-nuvlaboxes
  [{:keys [id] cimi-filter :filter :as params} request]
  (assoc params
    :nuvlaboxes
    (if id
      [(crud/retrieve-by-id id request)]
      (->> (crud/query
             (cond-> request
                     cimi-filter (assoc :cimi-params
                                        {:filter (parser/parse-cimi-filter cimi-filter)
                                         :last   10000})))
           :body
           :resources))))

(defn assoc-base-query-opts
  [{:keys [from to granularity raw custom-es-aggregations predefined-aggregations mode nuvlaboxes] :as params}]
  (assoc params
    :base-query-opts
    (cond->
      {:mode                    mode
       :nuvlaboxes              nuvlaboxes
       :nuvlaedge-ids           (concat (map :id nuvlaboxes))
       :from                    from
       :to                      to
       :granularity             granularity
       :raw                     raw
       :custom-es-aggregations  custom-es-aggregations
       :predefined-aggregations predefined-aggregations}
      predefined-aggregations
      (assoc :ts-interval (status-utils/granularity->ts-interval granularity)))))

(defn assoc-datasets-opts
  [{:keys [mode] :as params}]
  (assoc params
    :datasets-opts
    (case mode
      :single-edge-query (single-edge-datasets)
      :multi-edge-query (multi-edge-datasets))))

(defn throw-unknown-datasets
  [{:keys [datasets datasets-opts] :as params}]
  (when-not (every? (set (keys datasets-opts)) datasets)
    (logu/log-and-throw-400 (str "unknown datasets: "
                                 (str/join "," (sort (set/difference (set datasets)
                                                                     (set (keys datasets-opts))))))))
  params)

(defn throw-csv-multi-dataset
  [{:keys [datasets accept-header] :as params}]
  (when (and (= "text/csv" accept-header) (not= 1 (count datasets)))
    (logu/log-and-throw-400 (str "exactly one dataset must be specified with accept header 'text/csv'")))
  params)

(defn run-queries
  [{:keys [datasets base-query-opts datasets-opts] :as params}]
  (assoc params
    :resps
    (map (fn [dataset-key]
           (let [{:keys [metric pre-process-fn post-process-fn] :as dataset-opts} (get datasets-opts dataset-key)
                 {:keys [predefined-aggregations] :as query-opts} (merge base-query-opts dataset-opts)
                 query-fn   (case metric
                              "availability" utils/query-availability
                              utils/query-metrics)
                 query-opts (if pre-process-fn (pre-process-fn query-opts) query-opts)]
             (cond->> (query-fn query-opts)
                      post-process-fn ((fn [resp] (second (post-process-fn [query-opts resp]))))
                      predefined-aggregations (keep-response-aggs-only query-opts))))
         datasets)))

(defn json-data-response
  [{:keys [datasets resps]}]
  (r/json-response (zipmap datasets resps)))

(defn csv-response
  [{:keys [raw datasets datasets-opts mode resps] :as options}]
  (let [{:keys [csv-export-fn aggregations response-aggs] group-by-field :group-by}
        (get datasets-opts (first datasets))
        dimension-keys (case mode
                         :single-edge-query
                         [:nuvlaedge-id]
                         :multi-edge-query
                         [:nuvlaedge-count])
        #_csv-data       #_(if raw
                             (utils/raw-data->csv dimension-keys (first resps))
                             (utils/metrics-data->csv
                               (cond-> dimension-keys
                                       group-by-field (conj group-by-field))
                               (or response-aggs (keys aggregations))
                               (first resps)))]
    (when-not csv-export-fn
      (logu/log-and-throw-400 (str "csv export not supported for dataset " (first datasets))))
    (r/csv-response "export.csv" (csv-export-fn options))))

(defn send-data-response
  [{:keys [accept-header] :as options}]
  (case accept-header
    (nil "application/json")                                ; by default return a json response
    (json-data-response options)
    "text/csv"
    (csv-response options)))

(defn query-data
  [params request]
  (-> params
      (parse-params)
      (throw-mandatory-dataset-parameter)
      (throw-mandatory-from-to-parameters)
      (throw-from-not-before-to)
      (throw-mandatory-granularity-parameter)
      (throw-too-many-data-points)
      (throw-custom-es-aggregations-checks)
      (throw-response-format-not-supported)
      (assoc-nuvlaboxes request)
      (assoc-base-query-opts)
      (assoc-datasets-opts)
      (throw-unknown-datasets)
      (throw-csv-multi-dataset)
      (run-queries)
      (send-data-response)))

(defmethod crud/do-action [resource-type "data"]
  [{:keys [params] {accept-header "accept"} :headers :as request}]
  (query-data (assoc params :mode :single-edge-query
                            :accept-header accept-header) request))

(defn bulk-query-data
  [_ {:keys [body] {accept-header "accept"} :headers :as request}]
  (query-data (assoc body :mode :multi-edge-query
                          :accept-header accept-header) request))

(def validate-bulk-data-body (u/create-spec-validation-request-body-fn
                               ::nuvlabox/bulk-data-body))

(defn bulk-data
  [request bulk-impl]
  (-> request
      validate-bulk-data-body
      bulk-impl))

(def bulk-data-impl (std-crud/generic-bulk-operation-fn resource-type collection-acl bulk-query-data))

(defmethod crud/bulk-action [resource-type "data"]
  [request]
  (bulk-data request bulk-data-impl))


;;
;; Set operation
;;

;;
;; operations for states for owner are:
;;
;;                edit delete activate commission decommission unsuspend heartbeat set-offline
;; NEW             Y     Y       Y
;; ACTIVATED       Y                       Y           Y
;; COMMISSIONED    Y                       Y           Y                     Y          Y
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
        set-offline-op       (u/action-map id utils/action-set-offline)
        can-manage?          (a/can-manage? resource request)]
    (assoc resource
      :operations
      (cond-> []
              (a/can-edit? resource request) (conj edit-op)
              (and (a/can-delete? resource request)
                   (utils/can-delete? resource)) (conj delete-op)
              (and
                (a/is-admin-request? request)
                (utils/can-set-offline? resource)) (conj set-offline-op)
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
                      (utils/can-heartbeat? resource) (conj heartbeat-op))))))

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nuvlabox/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nuvlabox/schema)
  (md/register resource-metadata))
