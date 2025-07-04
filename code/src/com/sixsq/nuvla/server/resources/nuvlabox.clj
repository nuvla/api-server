(ns com.sixsq.nuvla.server.resources.nuvlabox
  "
The core `nuvlabox` resource that contains only those attributes required in
all subtypes of this resource. Versioned subclasses define the attributes for a
particular NuvlaBox release.
"
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as acl-resource]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.auth.utils.acl :as acl-utils]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.event-config :as ec]
    [com.sixsq.nuvla.server.resources.common.event-context :as ectx]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.deployment :as deployment]
    [com.sixsq.nuvla.server.resources.job.interface :as job-interface]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.data-utils :as data-utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.workflow-utils :as wf-utils]
    [com.sixsq.nuvla.server.resources.resource-log :as resource-log]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.common-body :as common-body]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox :as nuvlabox]
    [com.sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [com.sixsq.nuvla.server.util.log :as logu]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]
    [jsonista.core :as j]))

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
               :output-message "application/json"}
              {:name           utils/action-coe-resource-actions
               :uri            utils/action-coe-resource-actions
               :description    "allow to job executor execute actions at coe level"
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
        (wf-utils/update-peripherals id acl)
        (wf-utils/update-playbooks id acl))

      (when (value-changed? current-nb updated-nb :name)
        (deployment/bulk-update-nuvlabox-name-as-admin updated-nb))

      )))


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
  (let [id       (str resource-type "/" uuid)
        nuvlabox (crud/retrieve-by-id-as-admin id)]
    (ectx/add-to-context :acl (:acl nuvlabox))
    (ectx/add-to-context :resource nuvlabox)
    (try
      (-> nuvlabox
          (a/throw-cannot-delete request)
          (u/throw-cannot-do-action utils/can-delete? "delete"))
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
  (u/throw-cannot-do-action
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
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)]
    (try
      (let [nuvlabox (-> (crud/retrieve-by-id-as-admin id)
                         (a/throw-cannot-manage request)
                         (u/throw-cannot-do-action
                           utils/can-commission? "commission")
                         u/update-timestamps
                         (commission request)
                         crud/validate)]

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
        (dissoc :coe-list)
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
  [{:keys [id acl] :as _resource} request]
  (try
    (let [{{job-id     :resource-id
            job-status :status} :body} (job-utils/create-job id "decommission_nuvlabox"
                                                             acl
                                                             (auth/current-user-id request)
                                                             :priority 50)
          job-msg (str "decommissioning " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response
                 "unable to create async job to decommission nuvlabox resources" 500 id)))
      (ectx/add-linked-identifier job-id)
      ;; Legacy event
      ;; (event-utils/create-event id job-msg acl)
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "decommission"]
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)]
    (try
      (-> (crud/retrieve-by-id-as-admin id)
          (a/throw-cannot-manage request)
          (u/throw-cannot-do-action
            utils/can-decommission? "decommission")
          (decommission-sync request)
          (decommission-async request))
      (catch Exception e
        (or (ex-data e) (throw e))))))

(defn set-online!
  [{:keys [id nuvlabox-status heartbeat-interval online]
    :or   {heartbeat-interval utils/default-heartbeat-interval}
    :as   nuvlabox} online-new]
  (let [nb-status (utils/status-online-attributes
                    online online-new heartbeat-interval)]
    (r/throw-response-not-200
      (db/scripted-edit id {:refresh false
                            :body    {:doc {:online             online-new
                                            :heartbeat-interval heartbeat-interval}}}))
    (r/throw-response-not-200
      (db/scripted-edit nuvlabox-status {:refresh false
                                         :body    {:doc nb-status}}))
    (ka-crud/publish-on-edit
      "nuvlabox-status"
      (r/json-response (assoc nb-status :id nuvlabox-status
                                        :parent id
                                        :acl (:acl nuvlabox))))
    (data-utils/track-availability (assoc nb-status :parent id) false)
    nuvlabox))

(defmethod crud/do-action [resource-type utils/action-heartbeat]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-cannot-do-action utils/can-heartbeat? utils/action-heartbeat)
        (set-online! true)
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
        (u/throw-cannot-do-action
          utils/can-set-offline? utils/action-set-offline)
        (set-online! false))
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
  [{:keys [id acl] :as _nuvlabox} request]
  (log/warn "Checking API for NuvlaBox:" id)
  (try
    (let [{{job-id     :resource-id
            job-status :status} :body} (job-utils/create-job id "check_nuvlabox_api"
                                                             acl
                                                             (auth/current-user-id request)
                                                             :priority 50)
          job-msg (str "checking the API for NuvlaBox " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response
                 "unable to create async job to check nuvlabox api" 500 id)))
      (ectx/add-linked-identifier job-id)
      ;; Legacy event
      ;; (event-utils/create-event id job-msg acl)
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "check-api"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-cannot-do-action
          utils/can-check-api? "check-api")
        (check-api request))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Reboot action
;;

(defn reboot
  [{:keys [id acl] :as nuvlabox} request]
  (log/warn "Rebooting NuvlaBox:" id)
  (try
    (let [{{job-id     :resource-id
            job-status :status} :body} (job-utils/create-job id "reboot_nuvlabox"
                                                             (-> acl
                                                                 (a/acl-append :edit-data id)
                                                                 (a/acl-append :manage id))
                                                             (auth/current-user-id request)
                                                             :priority 50
                                                             :execution-mode (utils/get-execution-mode nuvlabox))
          job-msg (str "sending reboot request to NuvlaBox " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response
                 "unable to create async job to reboot nuvlabox" 500 id)))
      (ectx/add-linked-identifier job-id)
      ;; Legacy event
      ;; (event-utils/create-event id job-msg acl)
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "reboot"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-cannot-do-action utils/can-reboot? "reboot")
        (reboot request))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defn coe-resource-actions
  [{:keys [id acl] :as nuvlabox} {:keys [body] :as request}]
  (try
    (let [{{job-id     :resource-id
            job-status :status} :body} (job-utils/create-job id "coe_resource_actions"
                                                             (-> acl
                                                                 (a/acl-append :edit-data id)
                                                                 (a/acl-append :manage id))
                                                             (auth/current-user-id request)
                                                             :priority 50
                                                             :payload (j/write-value-as-string body)
                                                             :execution-mode (utils/get-execution-mode nuvlabox))
          job-msg (str "sending request to NuvlaBox " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response
                 "unable to create async job" 500 id)))
      (ectx/add-linked-identifier job-id)
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(def validate-coe-resource-actions-body (u/create-spec-validation-request-body-fn
                                          ::nuvlabox/coe-resource-actions-body))

(defn throw-credentials-not-allowed
  [resource {{docker-actions :docker} :body :as request}]
  (doseq [{credential-id :credential} docker-actions]
    (when-let [credential (some-> credential-id crud/retrieve-by-id-as-admin)]
      (acl-resource/throw-cannot-view credential request)))
  resource)

(defmethod crud/do-action [resource-type utils/action-coe-resource-actions]
  [{{uuid :uuid} :params :as request}]
  (try
    (validate-coe-resource-actions-body request)
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-cannot-do-action utils/can-coe-resource-actions? utils/action-coe-resource-actions)
        (throw-credentials-not-allowed request)
        (coe-resource-actions request))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defn get-context-coe-resource-actions
  [{:keys [target-resource payload] :as _job}]
  (let [nuvlabox-owner-req (some-> target-resource :href crud/retrieve-by-id-as-admin auth/get-owner-request)
        docker-actions     (some-> payload (j/read-value j/keyword-keys-object-mapper) :docker)
        credential-ids     (some->> docker-actions (keep :credential) set)
        credentials        (for [credential-id credential-ids
                                 :let [credential (some-> credential-id crud/retrieve-by-id-as-admin)]
                                 :when credential]
                             (do
                               (acl-resource/throw-cannot-view credential nuvlabox-owner-req)
                               credential))
        infra-services     (for [infra-service-id (set (map :parent credentials))
                                 :let [infra-service (some-> infra-service-id crud/retrieve-by-id-as-admin)]
                                 :when infra-service]
                             (do
                               (acl-resource/throw-cannot-view infra-service nuvlabox-owner-req)
                               infra-service))]
    (apply job-interface/get-context->response (concat credentials infra-services))))

(defmethod job-interface/get-context ["nuvlabox" "coe_resource_actions"]
  [resource]
  (get-context-coe-resource-actions resource))

;;
;; Cluster action
;;


(defn cluster-nuvlabox
  [{:keys [id acl] :as nuvlabox} request
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
            job-status :status} :body} (job-utils/create-job
                                         id (str "nuvlabox_cluster_" (str/replace cluster-action #"-" "_"))
                                         (-> acl
                                             (a/acl-append :edit-data id)
                                             (a/acl-append :manage id))
                                         (auth/current-user-id request)
                                         :priority 50
                                         :execution-mode (utils/get-execution-mode nuvlabox)
                                         :payload (j/write-value-as-string payload))
          job-msg (str "running cluster action " cluster-action " on NuvlaBox " id
                       ", with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response "unable to create async job to cluster NuvlaBox" 500 id)))
      (ectx/add-linked-identifier job-id)
      ;; Legacy event
      ;; (event-utils/create-event id job-msg acl)
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
          (u/throw-cannot-do-action
            utils/can-cluster-nuvlabox? "cluster-nuvlabox")
          (cluster-nuvlabox request cluster-action nuvlabox-manager-status token advertise-addr)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Add ssh-key action
;;


(defn add-ssh-key
  [{:keys [id acl] :as nuvlabox} request ssh-credential]
  (log/warn "Adding new SSH key for NuvlaBox:" id)
  (try
    (let [cred-id (:id ssh-credential)
          {{job-id     :resource-id
            job-status :status} :body} (job-utils/create-job
                                         id "nuvlabox_add_ssh_key"
                                         (-> acl
                                             (a/acl-append :edit-data id)
                                             (a/acl-append :manage id))
                                         (auth/current-user-id request)
                                         :affected-resources [{:href cred-id}]
                                         :priority 50
                                         :execution-mode (utils/get-execution-mode nuvlabox))
          job-msg (str "asking NuvlaBox "
                       id " to add SSH key "
                       cred-id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response
                 "unable to create async job to add SSH key to NuvlaBox" 500 id)))
      (ectx/add-linked-identifier job-id)
      ;; Legacy event
      ;; (event-utils/create-event id job-msg acl)
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
                          (u/throw-cannot-do-action
                            utils/can-add-ssh-key? "add-ssh-key"))
          acl         (:acl nuvlabox)
          credential  (if ssh-cred-id
                        (crud/retrieve-by-id-as-admin ssh-cred-id)
                        (wf-utils/create-ssh-key
                          {:acl      acl
                           :template {:href "credential-template/generate-ssh-key"}}))]

      (crud/retrieve-by-id (:id credential) request)
      (add-ssh-key nuvlabox request credential))
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
  [{:keys [id acl] :as nuvlabox} request ssh-credential-id]
  (if (nil? ssh-credential-id)
    (logu/log-and-throw-400 "SSH credential ID is missing")
    (do
      (log/warn "Removing SSH key " ssh-credential-id " from NuvlaBox " id)
      (try
        (let [{{job-id     :resource-id
                job-status :status} :body} (job-utils/create-job
                                             id "nuvlabox_revoke_ssh_key"
                                             (-> acl
                                                 (a/acl-append :edit-data id)
                                                 (a/acl-append :manage id))
                                             (auth/current-user-id request)
                                             :affected-resources [{:href ssh-credential-id}]
                                             :priority 50
                                             :execution-mode (utils/get-execution-mode nuvlabox))
              job-msg (str "removing SSH key " ssh-credential-id
                           " from NuvlaBox " id " with async " job-id)]
          (when (not= job-status 201)
            (throw (r/ex-response
                     "unable to create async job to remove SSH key from NuvlaBox" 500 id)))
          (ectx/add-linked-identifier job-id)
          ;; Legacy event
          ;; (event-utils/create-event id job-msg acl)
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
          (u/throw-cannot-do-action
            utils/can-revoke-ssh-key? "revoke-ssh-key")
          (revoke-ssh-key request ssh-cred-id)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod job-interface/get-context ["nuvlabox" "nuvlabox_revoke_ssh_key"]
  [resource]
  (get-context-ssh-credential resource))


;;
;; Update NuvlaBox Engine action
;;

(defn update-nuvlabox
  [{:keys [id acl] :as nuvlabox}
   {{:keys [nuvlabox-release payload parent-job]} :body :as request}]
  (if (nil? nuvlabox-release)
    (logu/log-and-throw-400 "Target NuvlaBox release is missing")
    (crud/retrieve-by-id nuvlabox-release request))
  (let [{{job-id     :resource-id
          job-status :status} :body} (job-utils/create-job
                                       id "nuvlabox_update"
                                       (-> acl
                                           (a/acl-append :edit-data id)
                                           (a/acl-append :manage id))
                                       (auth/current-user-id request)
                                       :affected-resources [{:href nuvlabox-release}]
                                       :priority 50
                                       :execution-mode (utils/get-execution-mode nuvlabox)
                                       :payload (when-not (str/blank? payload) payload)
                                       :parent-job parent-job)
        job-msg (str "updating NuvlaBox " id " with target release " nuvlabox-release
                     ", with async " job-id)]
    (when (not= job-status 201)
      (throw (r/ex-response "unable to create async job to update NuvlaBox" 500 id)))
    (ectx/add-linked-identifier job-id)
    (r/map-response job-msg 202 id job-id)))


(defmethod crud/do-action [resource-type "update-nuvlabox"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (u/throw-cannot-do-action utils/can-update-nuvlabox? "update-nuvlabox")
        (update-nuvlabox request))
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
        (u/throw-cannot-do-action
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
        (u/throw-cannot-do-action utils/can-disable-host-level-management?
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
        (u/throw-cannot-do-action utils/can-enable-emergency-playbooks?
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
      (u/throw-cannot-do-action utils/can-create-log? "create-log")
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
        (u/throw-cannot-do-action utils/can-generate-new-api-key?
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
          (u/throw-cannot-do-action utils/can-unsuspend? "unsuspend"))
      (crud/edit-by-id-as-admin id {:state utils/state-commissioned}))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type "data"]
  [{:keys [params] :as request}]
  (data-utils/wrapped-query-data
    (assoc params :mode :single-edge-query)
    request))

(defn bulk-query-data
  [_ {:keys [body] :as request}]
  (data-utils/wrapped-query-data
    (assoc body :mode :multi-edge-query)
    request))

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

(def bulk-action-impl (std-crud/bulk-action-fn resource-type collection-acl collection-type))

(defmethod crud/bulk-action [resource-type "bulk-update"]
  [request]
  (bulk-action-impl request))


;;
;; Set operation
;;

;;
;; operations for states for owner are:
;;
;;                edit delete activate commission decommission unsuspend heartbeat set-offline coe-resource-actions
;; NEW             Y     Y       Y
;; ACTIVATED       Y                       Y           Y
;; COMMISSIONED    Y                       Y           Y                     Y          Y                Y
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
        coe-actions-op       (u/action-map id utils/action-coe-resource-actions)
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
                      (utils/can-heartbeat? resource) (conj heartbeat-op)
                      (utils/can-coe-resource-actions? resource) (conj coe-actions-op))))))


;;
;; Events
;;

(defmethod ec/events-enabled? resource-type
  [_resource-type]
  true)


(defmethod ec/log-event? "nuvlabox.add"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.edit"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.delete"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.activate"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.commission"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.decommission"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.reboot"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.add-ssh-key"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.revoke-ssh-key"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.update-nuvlabox"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.enable-host-level-management"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.disable-host-level-management"
  [_event _response]
  true)

(defmethod ec/log-event? "nuvlabox.unsuspend"
  [_event _response]
  true)

(defmethod ec/log-event? (str "nuvlabox." utils/action-coe-resource-actions)
  [_event _response]
  true)

(defmethod ec/event-description "nuvlabox.activate"
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " activated nuvlabox"))
    "Nuvlabox activation attempt failed"))


(defmethod ec/event-description "nuvlabox.commission"
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " commissioned nuvlabox"))
    "Nuvlabox commissioning attempt failed"))


(defmethod ec/event-description "nuvlabox.decommission"
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " decommissioned nuvlabox"))
    "Nuvlabox decommission attempt failed"))


(defmethod ec/event-description "nuvlabox.reboot"
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " rebooted nuvlabox"))
    "Nuvlabox reboot attempt failed"))


(defmethod ec/event-description "nuvlabox.add-ssh-key"
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " added ssh key to nuvlabox"))
    "Nuvlabox ssh key addition attempt failed"))


(defmethod ec/event-description "nuvlabox.revoke-ssh-key"
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " revoked ssh key from nuvlabox"))
    "Nuvlabox commission attempt failed"))


(defmethod ec/event-description "nuvlabox.update-nuvlabox"
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " updated nuvlabox"))
    "Nuvlabox update attempt failed"))


(defmethod ec/event-description "nuvlabox.enable-host-level-management"
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " enabled host-level management on nuvlabox"))
    "Nuvlabox host-level management enabling attempt failed"))


(defmethod ec/event-description "nuvlabox.disable-host-level-management"
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " disabled host-level management on nuvlabox"))
    "Nuvlabox host-level management disabling attempt failed"))


(defmethod ec/event-description "nuvlabox.unsuspend"
  [{:keys [success] {:keys [user-id]} :authn-info :as _event} & _]
  (if success
    (when-let [user-name (or (some-> user-id crud/retrieve-by-id-as-admin1 :name) user-id)]
      (str user-name " unsuspended nuvlabox"))
    "Nuvlabox unsuspend attempt failed"))

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nuvlabox/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nuvlabox/schema)
  (md/register resource-metadata))
