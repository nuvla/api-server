(ns sixsq.nuvla.server.resources.pricing
  "
These resources describe pricing catalogue.
"
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.pricing :as pricing]
    [sixsq.nuvla.server.resources.pricing.utils :as pu]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.auth.acl-resource :as a]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const resource-id (str resource-type "/catalogue"))


(def collection-acl {:query ["group/nuvla-anon"]
                     :add   ["group/nuvla-admin"]})


;;
;; initialization: common schema for all user creation methods
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::pricing/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::pricing/schema)
  (md/register resource-metadata))


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::pricing/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (assoc resource :acl {:owners    ["group/nuvla-admin"]
                        :view-data ["group/nuvla-anon"]}))


;;
;; id
;;

(defmethod crud/new-identifier resource-type
  [resource resource-name]
  (assoc resource :id resource-id))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [request]
  (-> request
      (assoc :body (pu/build-nuvla-catalogue))
      (add-impl)))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))

(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [regenerate-op (u/action-map id :regenerate)
        can-manage?   (a/can-manage? resource request)]
    (cond-> (crud/set-standard-operations resource request)
            can-manage? (update :operations conj regenerate-op))))

(def edit-impl (std-crud/edit-fn resource-type))

(defmethod crud/do-action [resource-type "regenerate"]
  [request]
  (-> request
      (assoc :body (pu/build-nuvla-catalogue))
      (edit-impl)))