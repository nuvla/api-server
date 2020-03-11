(ns sixsq.nuvla.server.resources.nuvlabox-peripheral
  "
The nuvlabox-peripheral resource represents a peripheral attached to a
nuvlabox.
"
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-peripheral :as nb-peripheral]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:add   ["group/nuvla-user"]
                     :query ["group/nuvla-user"]})


;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given nuvlabox-peripheral resource against a specific
           version of the schema."
          :version)


(defmethod validate-subtype :default
  [{:keys [version] :as resource}]
  (if version
    (throw (r/ex-bad-request (str "unsupported nuvlabox-peripheral version: " version)))
    (throw (r/ex-bad-request "missing nuvlabox-peripheral version"))))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))

;;
;; acl
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (when-let [nuvlabox-id (:parent resource)]
    (let [{nuvlabox-acl :acl} (crud/retrieve-by-id-as-admin nuvlabox-id)]
      (assoc resource :acl
                      (-> nuvlabox-acl
                          (utils/set-acl-nuvlabox-view-only {:owners [nuvlabox-id]})
                          (assoc :manage (:view-acl nuvlabox-acl)))))))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; Set operation
;;

(defn has-class-video?
  [{:keys [classes] :as resource}]
  (contains? (set (map str/lower-case classes)) "video"))

(defmethod crud/set-operations resource-type
  [{:keys [id data-gateway-enabled] :as resource} request]
  (let [enable-stream-op  (u/action-map id :enable-stream)
        disable-stream-op (u/action-map id :disable-stream)
        can-manage?       (a/can-manage? resource request)
        class-video?      (has-class-video? resource)]
    (cond-> (crud/set-standard-operations resource request)
            (and can-manage?
                 class-video?) (update :operations conj (if data-gateway-enabled
                                                          disable-stream-op
                                                          enable-stream-op)))))


(defn create-job
  [resource {{uuid :uuid} :params :as request} action]
  (try
    (let [id (str resource-type "/" uuid)]
      (a/throw-cannot-manage resource request)

      (let [user-id (auth/current-user-id request)
            {{job-id     :resource-id
              job-status :status} :body} (job/create-job id action
                                                         {:owners   ["group/nuvla-admin"]
                                                          :edit-acl [user-id]}
                                                         :priority 50)
            job-msg (str "starting " id " with async " job-id)]
        (when (not= job-status 201)
          (throw (r/ex-response
                   (format "unable to create async job to %s" action) 500 id)))
        (r/map-response job-msg 202 id job-id)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn throw-data-gateway-already-enabled
  [{:keys [data-gateway-enabled] :as resource}]
  (if data-gateway-enabled
    (logu/log-and-throw-400 "NuvlaBox peripheral data gateway already enabled!")
    resource))


(defn throw-data-gateway-already-disabled
  [{:keys [data-gateway-enabled] :as resource}]
  (if data-gateway-enabled
    resource
    (logu/log-and-throw-400 "NuvlaBox peripheral data gateway already disabled!")))


(defn throw-doesnt-have-class-video
  [resource]
  (if (has-class-video? resource)
    resource
    (logu/log-and-throw-400 "NuvlaBox peripheral is not class video!")))


(defmethod crud/do-action [resource-type "enable-stream"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (throw-doesnt-have-class-video)
          (throw-data-gateway-already-enabled)
          (create-job request "enable-stream")))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "disable-stream"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (throw-data-gateway-already-disabled)
          (throw-doesnt-have-class-video)
          (create-job request "disable-stream")))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-peripheral/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nb-peripheral/schema)
  (md/register resource-metadata))
