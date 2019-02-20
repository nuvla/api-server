(ns sixsq.nuvla.server.resources.infrastructure-template
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-template :as infra-tpl]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ANON"
                              :type      "ROLE"
                              :right     "VIEW"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})


(def templates (atom {}))


(defn complete-resource
      [{:keys [type] :as resource}]
      (when type
            (let [id (str resource-type "/" type)]
                 (-> resource
                     (merge {:id            id
                             :resource-type resource-type})
                     u/update-timestamps))))


(defn register
      [resource]
      (when-let [{:keys [id] :as full-resource} (complete-resource resource)]
                (swap! templates assoc id full-resource)
                (log/info "loaded InfrastructureTemplate" id)))

;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given resource against the specific
           InfrastructureTemplate subtype schema."
          :type)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown InfrastructureTemplate type: " (:type resource)) resource)))


(defmethod crud/validate
  resource-type
  [resource]
  (validate-subtype resource))

;;
;; identifiers for these resources are the same as the :instance value
;;
;(defmethod crud/new-identifier resource-type
;  [{:keys [instance method] :as resource} resource-name]
;  (let [new-id (if (= method instance)
;                 instance
;                 (str method "-" instance))]
;    (assoc resource :id (str resource-type "/" new-id))))


;;
;; CRUD operations
;;

(defmethod crud/add resource-type
           [request]
           (throw (r/ex-bad-method request)))


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
;; initialization: create metadata for this collection
;;

(defn initialize
  []
  (md/register (gen-md/generate-metadata ::ns ::infra-tpl/schema)))

