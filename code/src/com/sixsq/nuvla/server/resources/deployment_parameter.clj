(ns com.sixsq.nuvla.server.resources.deployment-parameter
  "
These resources represent the parameters of a deployment that describe
properties of the deployment, for example, the IP address of the container or a
configuration option.
"
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.deployment-parameter :as deployment-parameter]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user"]
                     :bulk-delete ["group/nuvla-user"]})


(defn parameter->id
  [{:keys [parent node-id name] :as _parameter}]
  (->> (str/join ":" [parent node-id name])
       u/from-data-uuid
       (str resource-type "/")))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [{:keys [parent] :as resource} request]
  (-> resource
      (a/add-acl request)
      (a/acl-append-resource :edit-acl parent)))


(def validate-fn (u/create-spec-validation-fn ::deployment-parameter/deployment-parameter))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; set the resource identifier to "deployment-parameter/predictable-uuid3-from-string"
;;

(defmethod crud/new-identifier resource-type
  [parameter _resource-name]
  (->> (parameter->id parameter)
       (assoc parameter :id)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [parent]} :body :as request}]
  (crud/retrieve-by-id parent request)
  ;; 2024-07-18: commenting out event creation on deployment parameter "ss:state" as no usages were identified.
  ;; Leaving it commented out for the moment, for easy reversal, in case any problems arise
  ;(when (= name "ss:state")
  ;  (event-utils/create-event parent value acl
  ;                            :severity "medium"
  ;                            :category "state"))
  (add-impl request))


(def edit-impl (std-crud/edit-fn resource-type
                                 :options {:refresh false}))


(defmethod crud/edit resource-type
  [{:keys [body] :as request}]
  (let [updated-body (dissoc body :parent :name :node-id)]
    (edit-impl (assoc request :body updated-body))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def bulk-delete-impl (std-crud/bulk-delete-fn resource-type collection-acl collection-type))


(defmethod crud/bulk-delete resource-type
  [request]
  (bulk-delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::deployment-parameter/deployment-parameter))


(defn initialize
  []
  (std-crud/initialize resource-type ::deployment-parameter/deployment-parameter)
  (md/register resource-metadata))
