(ns sixsq.nuvla.server.resources.evidence-record
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.service-attribute-namespace :as sn]
    [sixsq.nuvla.server.resources.service-catalog.utils :as sc]
    [sixsq.nuvla.server.resources.spec.evidence-record :as er]))


(def ^:const resource-type (u/ns->type *ns*))

(def ^:const resource-name "EvidenceRecord")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "EvidenceRecordCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})


(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl resource request))

;;
;; multimethods for validation and operations
;;

(defn- validate-attributes
  [resource]
  (let [valid-prefixes (sn/all-prefixes)
        resource-payload (dissoc resource :acl :id :resourceURI :name :description
                                 :created :updated :tags :operations :class :planID :startTime :endTime :passed)
        validator (partial sc/valid-attribute-name? valid-prefixes)]
    (if (sc/valid-attributes? validator resource-payload)
      resource
      (sc/throw-wrong-namespace))))


(def validate-fn (u/create-spec-validation-fn ::er/evidence-record))
(defmethod crud/validate resource-uri
  [resource]
  (-> resource
      validate-fn
      validate-attributes))


(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

(defmethod crud/add resource-name
  [request]
  (add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-name))

(defmethod crud/delete resource-name
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri))

(defmethod crud/query resource-name
  [request]
  (query-impl request))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::er/evidence-record))
