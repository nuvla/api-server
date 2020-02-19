(ns sixsq.nuvla.server.resources.nuvlabox
  "
The core `nuvlabox` resource that contains only those attributes required in
all subtypes of this resource. Versioned subclasses define the attributes for a
particular NuvlaBox release.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.acl-resource :as acl-resource]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential.vpn-utils :as vpn-utils]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.nuvlabox.workflow-utils :as wf-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox :as nuvlabox]
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
(def ^:const latest-version 1)


(def ^:const default-refresh-interval 90)


;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given nuvlabox resource against a specific
           version of the schema."
          :version)


(defmethod validate-subtype :default
  [{:keys [version] :as resource}]
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
  [{:keys [id vpn-server-id owner] :as resource} request]
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
  [{{:keys [version refresh-interval vpn-server-id]
     :or   {version          latest-version
            refresh-interval default-refresh-interval}
     :as   body} :body :as request}]

  (when vpn-server-id
    (let [authn-info  (auth/current-authentication request)
          vpn-service (vpn-utils/get-service authn-info vpn-server-id)]
      (vpn-utils/check-service-subtype vpn-service)))

  (let [new-nuvlabox (assoc body :version version
                                 :state state-new
                                 :refresh-interval refresh-interval)]

    (add-impl (assoc request :body new-nuvlabox))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(defn edit-subresources
  [resource]
  (let [{:keys [id name acl nuvlabox-status
                infrastructure-service-group credential-api-key]
         :as   nuvlabox} (acl-utils/normalize-acl-for-resource resource)]

    (when nuvlabox-status
      (wf-utils/update-nuvlabox-status nuvlabox-status nuvlabox))

    (when infrastructure-service-group

      (wf-utils/update-infrastructure-service-group infrastructure-service-group nuvlabox)

      (let [swarm-id (wf-utils/update-swarm-service id name acl infrastructure-service-group nil nil)]
        (wf-utils/update-swarm-cred id name acl swarm-id nil nil nil)
        (wf-utils/update-swarm-token id name acl swarm-id "MANAGER" nil)
        (wf-utils/update-swarm-token id name acl swarm-id "WORKER" nil))

      (let [minio-id (wf-utils/update-minio-service id name acl infrastructure-service-group nil)]
        (wf-utils/update-minio-cred id name acl minio-id nil nil)))

    (when credential-api-key
      (wf-utils/update-nuvlabox-api-key credential-api-key nuvlabox))

    (wf-utils/update-peripherals id acl)))


(def edit-impl (std-crud/edit-fn resource-type))

(defn restricted-body
  [{:keys [id owner vpn-server-id] :as existing-resource}
   {:keys [acl name description location tags] :as body}]
  (cond-> existing-resource
          name (assoc :name name)
          description (assoc :description description)
          location (assoc :location location)
          tags (assoc :tags tags)
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
  [{:keys [nuvla/authn body params] :as request}]
  (let [id               (str resource-type "/" (:uuid params))
        is-admin?        (acl-resource/is-admin? authn)
        nuvlabox         (db/retrieve id request)
        updated-nuvlabox (if is-admin? body (restricted-body nuvlabox body))]

    (edit-subresources updated-nuvlabox)

    (edit-impl (assoc request :body updated-nuvlabox))))


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
      (delete-impl request)
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
                                                   (wf-utils/create-nuvlabox-api-key))]


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
          (fn [resource request] (:version resource)))


(defmethod commission :default
  [{:keys [version] :as resource} request]
  (if version
    (throw (r/ex-bad-request (str "unsupported nuvlabox version for commission action: " version)))
    (throw (r/ex-bad-request "missing nuvlabox version for commission action"))))


(defmethod crud/do-action [resource-type "commission"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (let [nuvlabox (db/retrieve id request)
              tags     (some-> body :tags set)]
          (-> nuvlabox
              (a/throw-cannot-manage request)
              (commission request))

          (db/edit (cond-> (assoc nuvlabox :state state-commissioned)
                           tags (assoc :tags tags)) request)

          (r/map-response "commission executed successfully" 200))
        (catch Exception e
          (or (ex-data e) (throw e)))))))


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
          (fn [resource request] (:version resource)))


(defmethod decommission-sync :default
  [{:keys [id acl] :as resource} request]
  (let [updated-acl (restrict-acl acl)]
    (-> resource
        (assoc :state state-decommissioning
               :acl updated-acl)
        (db/edit request))

    ;; read back the updated resource to ensure that ACL is fully normalized
    (crud/retrieve-by-id-as-admin id)))


(defmulti decommission-async
          "Creates a job to handle all the asynchronous clean up that is
           needed when decommissioning a nuvlabox."
          (fn [resource request] (:version resource)))


(defmethod decommission-async :default
  [{:keys [id acl] :as resource} request]
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
      (or (ex-data e) (throw e))))) 0


(defmethod crud/do-action [resource-type "decommission"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (-> (db/retrieve id request)
            (a/throw-cannot-manage request)
            (decommission-sync request)
            (decommission-async request))
        (catch Exception e
          (or (ex-data e) (throw e)))))))


;;
;;
;; NuvlaBox API operations
;;
;;

;;
;; Check API action
;;

(defn check-api
  [{:keys [id state acl] :as nuvlabox}]
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
          (or (ex-data e) (throw e))))
      (logu/log-and-throw-400 (str "invalid state for NuvlaBox actions: " state)))))


(defmethod crud/do-action [resource-type "check-api"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (check-api))
        (catch Exception e
          (or (ex-data e) (throw e)))))))


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
  (let [edit-op         (u/operation-map id :edit)
        delete-op       (u/operation-map id :delete)
        activate-op     (u/action-map id :activate)
        commission-op   (u/action-map id :commission)
        decommission-op (u/action-map id :decommission)
        check-api-op    (u/action-map id :check-api)
        ops             (cond-> []
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
                                     (#{state-commissioned} state)) (conj check-api-op))]
    (assoc resource :operations ops)))

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nuvlabox/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nuvlabox/schema)
  (md/register resource-metadata))


