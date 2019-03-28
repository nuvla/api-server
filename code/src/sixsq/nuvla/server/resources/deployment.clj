(ns sixsq.nuvla.server.resources.deployment
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl_resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment.utils :as deployment-utils]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.spec.deployment :as deployment-spec]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; validate deployment
;;

(def validate-fn (u/create-spec-validation-fn ::deployment-spec/deployment))
(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{:keys [identity body base-uri] :as request}]

  (a/throw-cannot-add collection-acl request)

  (let [deployment (-> body
                       (assoc :resource-type resource-type)
                       (assoc :state "CREATED")
                       (assoc :module (deployment-utils/resolve-module (:module body) identity))
                       (assoc :api-credentials (deployment-utils/generate-api-key-secret request))
                       (assoc :api-endpoint (str/replace-first base-uri #"/api/" ""))) ;; FIXME: Correct the value passed to the python API.

        create-response (add-impl (assoc request :body deployment))

        href (get-in create-response [:body :resource-id])

        msg (get-in create-response [:body :message])]

    (event-utils/create-event href msg (a/default-acl (a/current-authentication request)))

    create-response))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl (update request :body dissoc :api-credentials)))


(defn delete-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        (db/retrieve request)
        deployment-utils/verify-can-delete
        (a/can-edit-acl? request)
        (db/delete request))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; actions may be needed by certain authentication methods (notably external
;; methods like GitHub and OpenID Connect) to validate a given session
;;


(defmethod crud/set-operations resource-type
  [{:keys [id state] :as resource} request]
  (let [start-op {:rel (:start c/action-uri) :href (str id "/start")}
        stop-op {:rel (:stop c/action-uri) :href (str id "/stop")}]
    (cond-> (crud/set-standard-operations resource request)
            (#{"CREATED"} state) (update :operations conj start-op)
            (#{"STARTING" "STARTED" "ERROR"} state) (update :operations conj stop-op)
            (not (deployment-utils/can-delete? resource)) (update :operations deployment-utils/remove-delete))))


(defmethod crud/do-action [resource-type "start"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)
          user-id (:identity (a/current-authentication request))
          {{job-id     :resource-id
            job-status :status} :body} (job/create-job id "start_deployment"
                                                       {:owners   ["group/nuvla-admin"]
                                                        :edit-acl [user-id]}
                                                       :priority 50)
          job-msg (str "starting " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response "unable to create async job to start deployment" 500 id)))
      (-> id
          (db/retrieve request)
          (a/can-edit-acl? request)
          (assoc :state "STARTING")
          (db/edit request))
      (event-utils/create-event id job-msg (a/default-acl (a/current-authentication request)))
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "stop"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)
          user-id (:identity (a/current-authentication request))
          {{job-id     :resource-id
            job-status :status} :body} (job/create-job id "stop_deployment"
                                                       {:owners   ["group/nuvla-admin"]
                                                        :view-acl [user-id]}
                                                       :priority 60)
          job-msg (str "stopping " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response "unable to create async job to stop deployment" 500 id)))
      (-> id
          (db/retrieve request)
          (a/can-edit-acl? request)
          (assoc :state "STOPPING")
          (db/edit request))
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::deployment-spec/deployment))
