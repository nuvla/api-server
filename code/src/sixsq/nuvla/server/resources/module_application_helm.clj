(ns sixsq.nuvla.server.resources.module-application-helm
  "
This resource implements the Helm Application module.
"
  (:require [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.resources.resource-metadata :as md]
            [sixsq.nuvla.server.resources.spec.module-application-helm :as helm-app-spec]
            [sixsq.nuvla.server.util.metadata :as gen-md]
            [sixsq.nuvla.server.util.response :as r] ))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-admin"]
                     :add   ["group/nuvla-admin"]})


(def resource-acl {:owners ["group/nuvla-admin"]})


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::helm-app-spec/schema))
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


(defn- throw-mandatory-fields-missing
  [{{:keys [helm-repo-url
            helm-chart-name
            helm-absolute-url]} :body :as request}]
  (if (and (or (nil? helm-repo-url) (nil? helm-chart-name))
          (nil? helm-absolute-url))
      (throw (r/ex-response
               "Mandatory fields missing: (:helm-repo-url and :helm-chart-name) or :helm-absolute-url"
               400))
      request))


(defn- throw-mandatory-fields-mismatch
  [{{:keys [helm-repo-url
            helm-chart-name
            helm-absolute-url
            helm-chart-version]} :body :as request}]
  (if (and helm-absolute-url (or helm-repo-url helm-chart-name helm-chart-version))
      (throw (r/ex-response
               "Mandatory fields mismatch: :helm-absolute-url can't be provided with any of :helm-repo-url, :helm-chart-name, :helm-chart-version"
               400))
      request))


(defn- check-mandatory-fields
  [request]
  (-> request
      throw-mandatory-fields-missing
      throw-mandatory-fields-mismatch))


(defmethod crud/add resource-type
  [request]
  (-> request
      check-mandatory-fields
      add-impl))


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

(def resource-metadata (gen-md/generate-metadata ::ns ::helm-app-spec/schema))

(defn initialize
  []
  (std-crud/initialize resource-type ::helm-app-spec/schema)
  (md/register resource-metadata))
