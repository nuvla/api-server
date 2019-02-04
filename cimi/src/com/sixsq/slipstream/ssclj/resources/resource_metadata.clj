(ns com.sixsq.slipstream.ssclj.resources.resource-metadata
  "This resource provides metadata associated with other CIMI resourced. It
   can be used to understand the attributes, allow values, actions, and
   capabilities. This information is linked to a resource through the typeURI
   attribute."
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.auth.acl :as a]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata :as resource-metadata]
    [com.sixsq.slipstream.util.response :as r]
    [superstring.core :as str]))

(def ^:const resource-name "ResourceMetadata")

(def ^:const resource-tag (keyword (str (str/camel-case resource-name) "s")))

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name (str resource-name "Collection"))

(def ^:const resource-uri (str c/cimi-schema-uri resource-name))

(def ^:const collection-uri (str c/cimi-schema-uri collection-name))

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


(defn collection-wrapper-fn
  "Specialized version of this function that removes the adding
   of operations to the collection and entries.  These are already
   part of the stored resources."
  [resource-name collection-acl collection-uri collection-key]
  (fn [_ entries]
    (let [skeleton {:acl         collection-acl
                    :resourceURI collection-uri
                    :id          (u/de-camelcase resource-name)}]
      (assoc skeleton collection-key entries))))


(defn complete-resource
  "Completes the given document with server-managed information:
   resourceURI, timestamps, and ACL."
  [identifier resource]
  (when identifier
    (let [id (str resource-url "/" identifier)]
      (-> resource
          (dissoc :created :updated)
          (merge {:id          id
                  :resourceURI resource-uri
                  :acl         default-resource-acl})
          u/update-timestamps))))


(defn register
  "Registers a given ConfigurationTemplate resource and its description
   with the server.  The resource document (resource) and the description
   (desc) must be valid.  The key will be used to create the id of
   the resource as 'configuration-template/key'."
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
  resource-uri
  [resource]
  (validate-fn resource))


;;
;; only retrieve and query are supported CRUD operations
;;

(defmethod crud/add resource-name
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/retrieve resource-name
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)]
      (-> (get @templates id)
          (a/can-view? request)
          (r/json-response)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;; must override the default implementation so that the
;; data can be pulled from the atom rather than the database
(defmethod crud/retrieve-by-id resource-url
  [id]
  (try
    (get @templates id)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/edit resource-name
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/delete resource-name
  [request]
  (throw (r/ex-bad-method request)))


(defmethod crud/query resource-name
  [request]
  (a/can-view? {:acl collection-acl} request)
  (let [wrapper-fn (collection-wrapper-fn resource-name collection-acl collection-uri resource-tag)
        [count-before-pagination entries] ((juxt count vals) @templates)
        wrapped-entries (wrapper-fn request entries)
        entries-and-count (assoc wrapped-entries :count count-before-pagination)]
    (r/json-response entries-and-count)))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::resource-metadata/resource-metadata))
