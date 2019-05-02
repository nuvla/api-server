(ns sixsq.nuvla.server.resources.nuvlabox-identifier
  "
This resource represents a unique, human-readable identifier for a NuvlaBox
machine. They are assigned when a NuvlaBox machine is registered. Assigned
NuvlaboxIdentifier resources have a reference to a NuvlaboxRecord in the
:nuvlabox attribute.

The id field for these resources are generated from the MD5 hashes of the
:identifier value, guaranteeing that the identifiers are unique.

Only administrators can create and update these resources. Anyone can view the
NuvlaboxIdentifier resources and collection.
"
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-identifier :as spec]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def resource-acl {:owners ["group/nuvla-admin"]})


(def collection-acl {:query ["group/nuvla-admin"]
                     :add   ["group/nuvla-admin"]})


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::spec/nuvlabox-identifier))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; nuvlabox identifiers are visible to anyone
;; ignore provided ACL
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (assoc resource :acl resource-acl))


;;
;; ids for these resources are the hashed :identifier value
;;

(defmethod crud/new-identifier resource-type
  [{:keys [identifier] :as resource} resource-name]
  (->> identifier
       u/md5
       (str resource-type "/")
       (assoc resource :id)))

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
(defn initialize
  []
  (std-crud/initialize resource-type ::spec/nuvlabox-identifier)
  (md/register (gen-md/generate-metadata ::ns ::spec/nuvlabox-identifier)))
