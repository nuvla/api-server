(ns sixsq.nuvla.server.resources.configuration
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-template :as conf-tmpl]
    [sixsq.nuvla.util.response :as r]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const resource-tag :configurations)

(def ^:const resource-name "Configuration")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "ConfigurationCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def ^:const create-uri (str c/slipstream-schema-uri resource-name "Create"))

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

;;
;; validate subclasses of configurations
;;

(defmulti validate-subtype
          :service)

(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown Configuration type: " (:service resource)) resource)))

(defmethod crud/validate resource-uri
  [resource]
  (validate-subtype resource))

;;
;; validate create requests for subclasses of configurations
;;

(defn dispatch-on-service [resource]
  (get-in resource [:template :service]))

(defmulti create-validate-subtype dispatch-on-service)

(defmethod create-validate-subtype :default
  [resource]
  (throw (ex-info (format "unknown Configuration create type: %s %s" (dispatch-on-service resource) resource) resource)))

(defmethod crud/validate create-uri
  [resource]
  (create-validate-subtype resource))

;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl resource request))

;;
;; template processing
;;

(defmulti tpl->configuration
          "Transforms the ConfigurationTemplate into a Configuration resource."
          :service)

;; default implementation just removes href and updates the resourceURI
(defmethod tpl->configuration :default
  [{:keys [href] :as resource}]
  (cond-> resource
      href (assoc :template {:href href})
      true (dissoc :href)
      true (assoc :resourceURI resource-uri)))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

;; requires a ConfigurationTemplate to create new Configuration
(defmethod crud/add resource-name
  [{:keys [body] :as request}]
  (let [idmap {:identity (:identity request)}
        desc-attrs (u/select-desc-keys body)
        body (-> body
                 (assoc :resourceURI create-uri)
                 (std-crud/resolve-hrefs idmap true)
                 (update-in [:template] merge desc-attrs) ;; validate desc attrs
                 (crud/validate)
                 (:template)
                 (tpl->configuration))]
    (add-impl (assoc request :body (merge body desc-attrs)))))

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
;; use service as the identifier
;;

(defmethod crud/new-identifier resource-name
  [{:keys [service instance] :as resource} resource-name]
  (if-let [new-id (cond-> service
                          instance (str "-" instance))]
    (assoc resource :id (str (u/de-camelcase resource-name) "/" new-id))))


;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize resource-url nil))
