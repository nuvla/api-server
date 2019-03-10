(ns sixsq.nuvla.server.resources.resource-metadata
  "This resource provides metadata associated with other CIMI resourced. It
   can be used to understand the attributes, allow values, actions, and
   capabilities. This information is linked to a resource through the typeURI
   attribute."
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.resource-metadata :as resource-metadata]
    [sixsq.nuvla.server.utils :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def default-resource-acl {:owner {:principal "ADMIN"
                                   :type      "ROLE"}
                           :rules [{:principal "ANON"
                                    :type      "ROLE"
                                    :right     "VIEW"}]})


(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "ANON"
                              :type      "ROLE"
                              :right     "VIEW"}]})


;;
;; atom to keep track of the resource metadata documents for loaded resources
;;

(def templates (atom {}))


(defn complete-resource
  "Completes the given document with server-managed information:
   resource-type, timestamps, and ACL."
  [identifier resource]
  (when identifier
    (let [id (str resource-type "/" identifier)]
      (-> resource
          (dissoc :created :updated)
          (merge {:id            id
                  :resource-type resource-type
                  :acl           default-resource-acl})
          u/update-timestamps))))


(defn register
  "Registers a given resource-metadata resource with the server. The resource
   document must be valid. The `typeURI` attribute will be used to create the
   id of the resource as 'resource-metadata/typeURI'."
  [{:keys [typeURI] :as resource}]
  (when-let [full-resource (complete-resource typeURI resource)]
    (let [id (:id full-resource)]
      (try
        (crud/validate full-resource)
        (swap! templates assoc id full-resource)
        (log/info "registered resource metadata for" id)
        (catch Exception e
          (log/error "registration of resource metadata for" id "failed\n" (str e)))))))

;;
;; validation of documents
;;

(def validate-fn (u/create-spec-validation-fn ::resource-metadata/resource-metadata))
(defmethod crud/validate
  resource-type
  [resource]
  (validate-fn resource))


;;
;; only retrieve and query are supported CRUD operations
;;

(defmethod crud/add resource-type
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/retrieve resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (get @templates id)
          (a/can-view? request)
          (r/json-response)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;; must override the default implementation so that the
;; data can be pulled from the atom rather than the database
(defmethod crud/retrieve-by-id resource-type
  [id]
  (try
    (get @templates id)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/edit resource-type
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/delete resource-type
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/query resource-type
  [request]
  (a/can-view? {:acl collection-acl} request)
  (let [wrapper-fn (std-crud/collection-wrapper-fn resource-type collection-acl collection-type false false)
        [count-before-pagination entries] ((juxt count vals) @templates)
        wrapped-entries (wrapper-fn request entries)
        entries-and-count (assoc wrapped-entries :count count-before-pagination)]
    (r/json-response entries-and-count)))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-type ::resource-metadata/resource-metadata))
