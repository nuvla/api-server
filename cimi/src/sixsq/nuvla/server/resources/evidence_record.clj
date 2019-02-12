(ns sixsq.nuvla.server.resources.evidence-record
  (:require [clojure.string :as str]
            [sixsq.nuvla.auth.acl :as a]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.resources.service-attribute-namespace :as sn]
            [sixsq.nuvla.server.resources.service-catalog.utils :as sc]
            [sixsq.nuvla.server.resources.spec.evidence-record :as er]))

(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})


(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))

;;
;; multimethods for validation and operations
;;

(defn- validate-attributes
  [resource]
  (let [valid-prefixes (sn/all-prefixes)
        resource-payload (dissoc resource :acl :id :resourceURI :name :description
                                 :created :updated :properties :operations :class :planID :startTime :endTime :passed)
        validator (partial sc/valid-attribute-name? valid-prefixes)]
    (if (sc/valid-attributes? validator resource-payload)
      resource
      (sc/throw-wrong-namespace))))


(def validate-fn (u/create-spec-validation-fn ::er/evidence-record))
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
  (std-crud/initialize resource-type ::er/evidence-record))
