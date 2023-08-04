(ns sixsq.nuvla.server.resources.deployment-parameter
  "
These resources represent the parameters of a deployment that describe
properties of the deployment, for example, the IP address of the container or a
configuration option.
"
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.deployment-parameter :as deployment-parameter]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user"]
                     :bulk-delete ["group/nuvla-user"]})


(defn parameter->uuid
  [parent node-id parameter-name]
  (let [id (str/join ":" [parent node-id parameter-name])]
    (u/from-data-uuid id)))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [{:keys [parent] :as resource} request]
  (-> resource
      (a/acl-append-resource :edit-acl parent)
      (a/add-acl request)))


(def validate-fn (u/create-spec-validation-fn ::deployment-parameter/deployment-parameter))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; set the resource identifier to "deployment-parameter/predictable-uuid3-from-string"
;;

(defmethod crud/new-identifier resource-type
  [{:keys [parent node-id name] :as parameter} _resource-name]
  (->> (parameter->uuid parent node-id name)
       (str resource-type "/")
       (assoc parameter :id)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [parent name value acl]} :body :as request}]
  (when (= name "ss:state")
    #_(event-utils/create-event-old parent acl
                                    :state value
                                    :severity "medium"
                                    :category "state"))
  (add-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


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
