(ns sixsq.nuvla.server.resources.configuration
  (:require
    [sixsq.nuvla.auth.acl_resource :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:owners ["group/nuvla-admin"]})

;;
;; validate subclasses of configurations
;;

(defmulti validate-subtype
          :service)

(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown Configuration type: " (:service resource)) resource)))

(defmethod crud/validate resource-type
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

(defmethod crud/validate create-type
  [resource]
  (create-validate-subtype resource))

;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))

;;
;; template processing
;;

(defmulti tpl->configuration
          "Transforms the ConfigurationTemplate into a Configuration resource."
          :service)

;; default implementation just removes href and updates the resource-type
(defmethod tpl->configuration :default
  [{:keys [href] :as resource}]
  (cond-> resource
          href (assoc :template {:href href})
          true (dissoc :href)
          true (assoc :resource-type resource-type)))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

;; requires a ConfigurationTemplate to create new Configuration
(defmethod crud/add resource-type
  [{:keys [body] :as request}]
  (let [idmap {:identity (:identity request)}
        desc-attrs (u/select-desc-keys body)
        body (-> body
                 (assoc :resource-type create-type)
                 (std-crud/resolve-hrefs idmap true)
                 (update-in [:template] merge desc-attrs)   ;; validate desc attrs
                 (crud/validate)
                 (:template)
                 (tpl->configuration))]
    (add-impl (assoc request :body (merge body desc-attrs)))))

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
;; use service as the identifier
;;

(defmethod crud/new-identifier resource-type
  [{:keys [service instance] :as resource} resource-name]
  (if-let [new-id (cond-> service
                          instance (str "-" instance))]
    (assoc resource :id (str resource-name "/" new-id))))


;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize resource-type nil))
