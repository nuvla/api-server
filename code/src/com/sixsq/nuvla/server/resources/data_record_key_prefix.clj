(ns com.sixsq.nuvla.server.resources.data-record-key-prefix
  "
Every attribute in a `data-record` resource must be prefixed to avoid
collisions. The `data-record-key-prefix` resources maintain the mapping between
a prefix and the associated, complete URI.

A `data-record` resource cannot be uploaded to the server unless all of the
prefixes within the document have been defined.

Currently, only an administrator can create, update, or delete
`data-record-key-prefix` resources. These actions follow the standard API
patterns. Most users will only search these resources and look at the details
for a particular `data-record-key-prefix` resource.
"
  (:require
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.data-record-key-prefix :as key-prefix]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def resource-acl {:owners   ["group/nuvla-admin"]
                   :view-acl ["group/nuvla-user"]})


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-admin"]})


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::key-prefix/schema))
(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))

(defmethod crud/add-acl resource-type
  [resource _request]
  (assoc resource :acl resource-acl))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

;; TODO ACL: Roles are needed in two locations!  Should be unique way to specify authentication information.
(def ^:private all-query-map {:params         {:resource-name resource-type}
                              :request-method :get
                              :nuvla/authn    auth/internal-identity})

(defn extract-field-values
  "returns a set of the values of the field k (as a keyword) from the
   data-record-key-prefix resources that match the query"
  [query-map k]
  (->> query-map
       crud/query
       :body
       :resources
       (map k)
       set))

(defn all-prefixes
  []
  (extract-field-values all-query-map :prefix))

(defn colliding-id
  "returns the first data-record-key-prefix resource that has the same prefix OR uri"
  [prefix uri]
  (let [filter (parser/parse-cimi-filter (format "(prefix='%s') or (uri='%s')" prefix uri))]
    (-> all-query-map
        (assoc :cimi-params {:filter filter :first 1 :last 1})
        (extract-field-values :id)
        first)))

(defmethod crud/add resource-type
  [{{:keys [prefix uri]} :body :as request}]
  (if-let [id (colliding-id prefix uri)]
    (r/response-conflict id)
    (add-impl request)))

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

(defmethod crud/new-identifier resource-type
  [json _]
  (let [new-id (str resource-type "/" (:prefix json))]
    (assoc json :id new-id)))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-type ::key-prefix/schema)
  (md/register (gen-md/generate-metadata ::ns ::key-prefix/schema)))
