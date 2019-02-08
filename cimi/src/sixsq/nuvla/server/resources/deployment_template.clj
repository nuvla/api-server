(ns sixsq.nuvla.server.resources.deployment-template
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment.utils :as du]
    [sixsq.nuvla.server.resources.spec.credential-template]
    [sixsq.nuvla.server.resources.spec.deployment-template :as dt]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const generated-url (str resource-type "/generated"))


;; the templates are managed as in-memory resources, so modification
;; of the collection is not permitted, but users must be able to list
;; and view templates

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::dt/deployment-template))
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
  [{:keys [body] :as request}]
  (try
    (let [idmap (:identity request)
          deployment-template (du/create-deployment-template body idmap)]
      (add-impl (assoc request :body deployment-template)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


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
  (std-crud/initialize resource-type ::dt/template))
