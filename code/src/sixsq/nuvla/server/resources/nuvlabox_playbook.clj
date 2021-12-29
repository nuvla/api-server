(ns sixsq.nuvla.server.resources.nuvlabox-playbook
  "
The nuvlabox-playbook resource holds scripts that, when configured, 
can be executed by the NuvlaBox device, externally and independently of the 
NuvlaBox Engine software
"
  (:require
   [sixsq.nuvla.auth.acl-resource :as a]
   [sixsq.nuvla.auth.utils :as auth]
   [sixsq.nuvla.db.impl :as db]
   [sixsq.nuvla.server.resources.common.crud :as crud]
   [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
   [sixsq.nuvla.server.resources.common.utils :as u]
   [sixsq.nuvla.server.resources.job :as job]
   [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
   [sixsq.nuvla.server.resources.resource-metadata :as md]
   [sixsq.nuvla.server.resources.spec.nuvlabox-playbook :as nb-playbook]
   [sixsq.nuvla.server.util.log :as logu]
   [sixsq.nuvla.server.util.metadata :as gen-md]
   [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:add   ["group/nuvla-user"]
                     :query ["group/nuvla-user"]})


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-playbook/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))

;;
;; acl
;;

(defmethod crud/add-acl resource-type
  [resource _request]
  (when-let [nuvlabox-id (:parent resource)]
    (let [{nuvlabox-acl :acl} (crud/retrieve-by-id-as-admin nuvlabox-id)
          view-acl (:view-acl nuvlabox-acl)]
      (assoc resource
             :acl (cond-> (:acl resource)
                    (not-empty view-acl) (assoc :view-acl view-acl))))))


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
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-playbook/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nb-playbook/schema)
  (md/register resource-metadata))
