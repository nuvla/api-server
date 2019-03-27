(ns sixsq.nuvla.server.resources.evidence-record
  (:require
    [sixsq.nuvla.auth.acl_resource :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as sn]
    [sixsq.nuvla.server.resources.data.keys :as key-utils]
    [sixsq.nuvla.server.resources.spec.evidence-record :as evidence-record]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:owners   ["group/nuvla-admin"]
                     :edit-acl ["group/nuvla-user"]})


(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))

;;
;; multimethods for validation and operations
;;

(defn- validate-attributes
  [resource]
  (let [valid-prefixes (sn/all-prefixes)
        resource-payload (dissoc resource
                                 :acl :id :resource-type :name :description
                                 :created :updated :properties :operations
                                 :class :plan-id :start-time :end-time :passed)
        validator (partial key-utils/valid-attribute-name? valid-prefixes)]
    (if (key-utils/valid-attributes? validator resource-payload)
      resource
      (key-utils/throw-wrong-namespace))))


(def validate-fn (u/create-spec-validation-fn ::evidence-record/schema))
(defmethod crud/validate resource-type
  [resource]
  (-> resource
      validate-fn
      validate-attributes))


(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


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
  (std-crud/initialize resource-type ::evidence-record/schema))
