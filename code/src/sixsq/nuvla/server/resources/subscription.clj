(ns sixsq.nuvla.server.resources.subscription
  "
Collection for holding subscriptions.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.subscription :as subs-schema]
    [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user"]
                     :bulk-delete ["group/nuvla-user"]})


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::subs-schema/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::subs-schema/schema)
  (md/register resource-metadata))


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::subs-schema/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (let [{:keys [name description]} (:body request)
        name (if (not name)
               resource-type
               name)
        request (assoc-in request [:body :name] name)
        description (if (not description)
                      name
                      description)
        request (assoc-in request [:body :description] description)
        resp (add-impl request)]
    (ka-crud/publish-on-add resource-type resp :key "resource")
    resp))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (let [resp (edit-impl request)]
    (ka-crud/publish-on-edit resource-type resp :key "resource-id")
    resp))


(defn delete-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (let [resource-id   (str resource-type "/" uuid)
          resource (db/retrieve resource-id request)
          delete-response (-> resource
                              (a/throw-cannot-delete request)
                              (db/delete request))]
      (ka-crud/publish-tombstone resource-type (:resource-id resource))
      delete-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def bulk-delete-impl (std-crud/bulk-delete-fn resource-type collection-acl collection-type))
(defmethod crud/bulk-delete resource-type
  [request]
  (bulk-delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))

