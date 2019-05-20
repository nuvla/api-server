(ns sixsq.nuvla.server.resources.nuvlabox-record
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.spec.nuvlabox-record :as nuvlabox-record]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


(def ^:const state-new "NEW")


(def ^:const state-activated "ACTIVATED")


(def ^:const state-quarantined "QUARANTINED")


(def ^:const default-refresh-interval 90)


;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given nuvlabox-record resource against a specific
           version of the schema."
          :version)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unsupported nuvlabox-record version: " (:version resource)) resource)))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))


;;
;; use default method for generating an ACL
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [refresh-interval]
     :or   {refresh-interval default-refresh-interval}
     :as   body} :body :as request}]

  (let [new-nuvlabox (assoc body :state state-new
                                 :refresh-interval refresh-interval)]

    (add-impl (assoc request :body new-nuvlabox))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;; The delete will be handled asynchronously so that all resources associated
;; with the NuvlaBox will be removed as well.


(defn restrict-acl
  "Updates the given acl by giving the view-acl right to all owners, removing
   all edit-* rights, and setting the owners to only [\"group/nuvla-admin\"]."
  [{:keys [owners] :as acl}]
  (-> acl
      (dissoc :edit-meta :edit-data :edit-acl)
      (assoc :owners ["group/nuvla-admin"])
      (update-in [:view-acl] concat owners)))


(defmulti delete-sync
          "Executes the synchronous tasks associated with deleting a
           nuvlabox-record resource. This must always return the value of the
           resource that was passed in."
          (fn [resource request] (:version resource)))


(defmethod delete-sync :default
  [{:keys [id nuvlabox-status acl] :as resource} request]
  (let [updated-acl (restrict-acl acl)]
    (-> resource
        (assoc :state "DECOMMISSIONING"
               :acl updated-acl)
        (db/edit request))

    (-> nuvlabox-status
        crud/retrieve-by-id-as-admin
        (assoc :acl updated-acl)
        (db/edit request))

    ;; read back the updated resource to ensure that ACL is fully normalized
    (crud/retrieve-by-id-as-admin id)))


(defmulti delete-async
          "Creates a job to handle all the asynchronous clean up that is
           needed when deleting a nuvlabox-resource."
          (fn [resource request] (:version resource)))


(defmethod delete-async :default
  [{:keys [id acl] :as resource} request]
  (try
    (let [updated-acl (assoc acl :owners ["group/nuvla-user"])
          {{job-id     :resource-id
            job-status :status} :body} (job/create-job id "delete_nuvlabox"
                                                       updated-acl
                                                       :priority 50)
          job-msg (str "deleting " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response "unable to create async job to delete nuvlabox resources" 500 id)))
      (event-utils/create-event id job-msg updated-acl)
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/delete resource-type
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)]
    (try
      (-> (db/retrieve id request)
          (a/throw-cannot-delete request)
          (delete-sync request)
          (delete-async request))
      (catch Exception e
        (or (ex-data e) (throw e))))))


;;
;; Activate operation
;;

(defn activate
  [{:keys [id state acl] :as nuvlabox}]
  (if (= state state-new)
    (do
      (log/warn "Activating nuvlabox:" id)
      ;; FIXME: Uses identifier as claim to access nuvlabox-* resources.
      (let [new-acl (update acl :edit-acl (comp vec conj) id)
            activated-nuvlabox (-> nuvlabox
                                   (assoc :state state-activated)
                                   (assoc :acl new-acl)
                                   utils/create-nuvlabox-status
                                   utils/create-infrastructure-service-group)]
        activated-nuvlabox))
    (logu/log-and-throw-400 "Activation is not allowed")))


(defmethod crud/do-action [resource-type "activate"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)
          nuvlabox (db/retrieve id request)
          nuvlabox-activated (activate nuvlabox)
          api-secret-info (utils/create-nuvlabox-api-key nuvlabox-activated)]

      (db/edit nuvlabox-activated request)

      (r/json-response api-secret-info))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Quarantine operation
;;

(defn quarantine [{:keys [id state] :as resource}]
  (if (= state state-activated)
    (do
      (log/warn "Changing nuvlabox status to quarantined : " id)
      (assoc resource :state state-quarantined))
    (logu/log-and-throw-400 (str "Bad nuvlabox state " state))))


(defmethod crud/do-action [resource-type "quarantine"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (-> (db/retrieve id request)
            (a/throw-cannot-manage request)
            quarantine
            (db/edit request))
        (catch Exception e
          (or (ex-data e) (throw e)))))))

;;
;; Recommission operation
;;

(defmulti recommission
          "Recreates the infrastructure-service(s), credentials, etc. that are
           associated with this nuvlabox-record. The resources that are created
           depend on the version of nuvlabox-* resources being used."
          (fn [resource request] (:version resource)))


(defmethod recommission :default
  [resource request]
  (throw (ex-info (str "unsupported nuvlabox-record version for recommission: " (:version resource)) resource)))


(defmethod crud/do-action [resource-type "recommission"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (-> (db/retrieve id request)
            (a/throw-cannot-manage request)
            recommission request)
        (catch Exception e
          (or (ex-data e) (throw e)))))))


;;
;; Set operation
;;

(defmethod crud/set-operations resource-type
  [{:keys [id state] :as resource} request]
  (let [href-activate (str id "/activate")
        href-sc (str id "/quarantine")
        activate-op {:rel (:activate c/action-uri) :href href-activate}
        quarantine-op {:rel (:quarantine c/action-uri) :href href-sc}
        recommission-op {:rel (:recommission c/action-uri) :href href-sc}]
    (cond-> (crud/set-standard-operations resource request)
            (= state state-new) (update-in [:operations] conj activate-op)
            (= state state-activated) (update-in [:operations] conj quarantine-op)
            (= state state-activated) (update-in [:operations] conj recommission-op))))

;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::nuvlabox-record/schema))


