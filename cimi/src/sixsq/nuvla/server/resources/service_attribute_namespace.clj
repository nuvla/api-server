(ns sixsq.nuvla.server.resources.service-attribute-namespace
  "
Every attribute in a ServiceOffer resource must be namespaced to avoid
collisions. The ServiceAttributeNamespace resources maintain the mapping
between a namespace prefix and the associated, complete URI. The parameters are
described in the table below.

A ServiceOffer resource cannot be uploaded to the server unless all of the
namespace prefixes within the document have been defined.

Currently, only an administrator can create, update, or delete
ServiceAttributeNamespace resources. These actions follow the standard CIMI
patterns. Most users will only search these resources and look at the details
for a particular ServiceAttributeNamespace resource.

Parameter | Required  | Description
--------- | --------  | -----------
prefix | true | namespace prefix
uri | true | full URI associated with the prefix

Search for all of the ServiceAttributeNamespace resources.

```shell
curl https://nuv.la/api/service-attribute-namespace
```

Show the ServiceAttributeNamespace resource for the 'exoscale' prefix.

```shell
curl https://nuv.la/api/service-attribute-namespace/exoscale
```

```json
{
  \"id\" : \"service-attribute-namespace/exoscale\",
  \"resource-type\" : \"http://sixsq.com/slipstream/1/ServiceAttributeNamespace\",
  \"created\" : \"2017-04-27T08:41:39.470Z\",
  \"updated\" : \"2017-04-27T08:41:39.470Z\",

  \"prefix\" : \"exoscale\",
  \"uri\" : \"http://sixsq.com/slipstream/schema/1/connector/exoscale\",

  \"acl\" : {\"...\" : \"...\"}
}
```
"
  (:require
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.service-attribute-namespace :as san]
    [sixsq.nuvla.util.response :as response]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const collection-name "ServiceAttributeNamespaceCollection")

(def ^:const collection-uri collection-name)

(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "USER"
                            :type      "ROLE"
                            :right     "VIEW"}]})

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})

;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::san/service-attribute-namespace))
(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))

(defmethod crud/add-acl resource-type
  [resource request]
  (assoc resource :acl resource-acl))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

;; FIXME: Roles are needed in two locations!  Should be unique way to specify authentication information.
(def ^:private all-query-map {:identity       {:current         "slipstream",
                                               :authentications {"slipstream"
                                                                 {:identity "slipstream"
                                                                  :roles    ["ADMIN" "USER" "ANON"]}}}
                              :params         {:resource-name resource-type}
                              :user-roles     ["ADMIN" "USER" "ANON"]
                              :request-method :get})

(defn extract-field-values
  "returns a set of the values of the field k (as a keyword) from the
   ServiceAttributeNamespace resources that match the query"
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
  "returns the first ServiceAttributeNamespace resource that has the same prefix OR uri"
  [prefix uri]
  (let [filter (parser/parse-cimi-filter (format "(prefix='%s') or (uri='%s')" prefix uri))]
    (-> all-query-map
        (assoc :cimi-params {:filter filter :first 1 :last 1})
        (extract-field-values :id)
        first)))

(defmethod crud/add resource-type
  [{{:keys [prefix uri]} :body :as request}]
  (if-let [id (colliding-id prefix uri)]
    (response/response-conflict id)
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

(def query-impl (std-crud/query-fn resource-type collection-acl collection-uri))

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
  (std-crud/initialize resource-type ::san/service-attribute-namespace))
