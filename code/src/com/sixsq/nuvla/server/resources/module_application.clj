(ns com.sixsq.nuvla.server.resources.module-application
  "
This resource represents an application module that contain a Docker
stack compose file.
"
  (:require [com.sixsq.nuvla.server.resources.common.crud :as crud]
            [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
            [com.sixsq.nuvla.server.resources.common.utils :as u]
            [com.sixsq.nuvla.server.resources.resource-metadata :as md]
            [com.sixsq.nuvla.server.resources.spec.module-application :as module-application]
            [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-admin"]
                     :add   ["group/nuvla-admin"]})


(def resource-acl {:owners ["group/nuvla-admin"]})


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::module-application/schema))
(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource _request]
  (assoc resource :acl resource-acl))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::module-application/schema))

(defn initialize
  []
  (std-crud/initialize resource-type ::module-application/schema)
  (md/register resource-metadata))
