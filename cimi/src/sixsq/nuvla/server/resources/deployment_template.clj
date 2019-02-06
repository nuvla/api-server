(ns sixsq.nuvla.server.resources.deployment-template
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment.utils :as du]
    [sixsq.nuvla.server.resources.spec.credential-template]
    [sixsq.nuvla.server.resources.spec.deployment-template :as dt]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const resource-tag :deploymentTemplates)

(def ^:const resource-name "DeploymentTemplate")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "DeploymentTemplateCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def ^:const generated-url (str resource-url "/generated"))

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
(defmethod crud/validate resource-uri
  [resource]
  (validate-fn resource))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;


(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

(defmethod crud/add resource-name
  [{:keys [body] :as request}]
  (try
    (let [idmap (:identity request)
          deployment-template (du/create-deployment-template body idmap)]
      (add-impl (assoc request :body deployment-template)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(def retrieve-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-name))

(defmethod crud/edit resource-name
  [request]
  (edit-impl request))


(def delete-impl (std-crud/delete-fn resource-name))

(defmethod crud/delete resource-name
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))

(defmethod crud/query resource-name
  [request]
  (query-impl request))

;;
;; initialization
;;


(defn initialize
  []
  (std-crud/initialize resource-url ::dt/deploymentTemplate))
