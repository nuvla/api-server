(ns sixsq.nuvla.server.resources.deployment-parameter
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment :as d]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.spec.deployment-parameter :as deployment-parameter]
    [sixsq.nuvla.server.resources.spec.event :as event]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-admin"]})


(defn parameter->uiid
  [deployment-href node-id name]
  (let [id (str/join ":" [deployment-href node-id name])]
    (u/from-data-uuid id)))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


(def validate-fn (u/create-spec-validation-fn ::deployment-parameter/deployment-parameter))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; set the resource identifier to "deployment-parameter/predictable-uuid3-from-string"
;;

(defmethod crud/new-identifier resource-type
  [{:keys [deployment node-id name] :as parameter} resource-name]
  (->> (parameter->uiid (:href deployment) node-id name)
       (str resource-type "/")
       (assoc parameter :id)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [name value deployment acl]} :body :as request}]
  (when (= name "ss:state")
    (event-utils/create-event (:href deployment) value acl
                              :severity "medium"
                              :category "state"))
  (add-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


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
  (std-crud/initialize resource-type ::deployment-parameter/deployment-parameter))
