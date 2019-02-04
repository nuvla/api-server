(ns com.sixsq.slipstream.ssclj.resources.quota
  "
The collection of Quota resources defines the overall resource utilization
policy for the SlipStream server. Each Quota resource defines a limit on an
identified resource. For new resource requests, SlipStream will verify that the
request will not exceed the defined quotas before executing the request.
Currently, SlipStream only checks quotas when deploying new workloads.

The Quota resource follows the standard CIMI SCRUD patterns. It is **not** a
templated resource and uses the simple CIMI add pattern.

> NOTE: Because quotas can potentially impact a large number of users, only
administrators can create Quota resources.

An abbreviated example of a Quota resource that sets a limit (116) on the
total number (count:id) of VirtualMachine resources for the given credential
for the MYORG organization.

```json
{
  \"id\": \"quota/a6d547cd-3855-4b24-aae2-73ad0707d828\",
  \"resourceURI\": \"http://sixsq.com/slipstream/1/Quota\",

  \"name\": \"max_instances in connector/exoscale-ch-dk\",
  \"description\": \"limits regarding max_instances, for credential credential/45765716-33f0-46bb-b3de-7bef04a58998 in connector/exoscale-ch-dk\",

  \"updated\": \"2018-10-18T05:17:21.162Z\",
  \"created\": \"2018-08-06T05:30:03.214Z\",

  \"resource\": \"VirtualMachine\",
  \"selection\": \"credentials/href='credential/45765716-33f0-46bb-b3de-7bef04a58998'\"
  \"aggregation\": \"count:id\",

  \"limit\": 116,

  \"acl\": {
    \"owner\": {
      \"type\": \"ROLE\",
      \"principal\": \"ADMIN\"
    },
    \"rules\": [
      {
        \"right\": \"VIEW\",
        \"type\": \"ROLE\",
        \"principal\": \"MYORG:can_deploy\"
      }
    ]
  },
  \"operations\": [
    {
      \"rel\": \"http://sixsq.com/slipstream/1/action/collect\",
      \"href\": \"quota/a6d547cd-3855-4b24-aae2-73ad0707d828/collect\"
    }
  ]
}
```

## Quota Attributes

| Attribute&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Description |
| :---- | :---- |
| `resource` | Identifies the resource to which the quota applies. Current examples include 'VirtualMachine' and 'NuvlaBoxRecord', the source of the current debate. |
| `selection` | A CIMI filter expression that identifies the resources to include in the calculation of the current resource utilization. |
| `aggregation` | A CIMI aggregation expression that calculates the values for the current resource utilization. One number is calculated with the access rights of the user and another, with the access rights of 'super'. |
| `limit` | The value to compare the calculated resource utilization against. |
| `acl` |  Defines to whom a given quota resource applies. If a quota resource is visible to an entity (user, group, role, etc.), it applies to that entity. |

### Collect Action

The 'collect' action (shown in the operations section) can be executed
by sending an HTTP POST request to the given URL.

This action will calculate the Quota's aggregation by running the
defined selection filter against the specified resource collection.
This will be done twice: once as the user and once with as the
administrator.

The Quota resource will be returned with two attributes added:
`current` and `currentAll`.  These are described below.

| | |
| :---- | :---- |
| `current` | Aggregation result when run as the user. |
| `currentAll` | Aggregation result when run as the administrator. |

The enforcement algorithm for the resource will then accept or reject
a request by comparing the `current` or `currentAll` values to the
defined `limit`.  How the comparison is done and how multiple
quotas are combined are defined by the enforcement algorithm itself.

"
  (:require
    [com.sixsq.slipstream.auth.acl :as a]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.quota.utils :as quota-utils]
    [com.sixsq.slipstream.ssclj.resources.spec.quota :as quota]
    [com.sixsq.slipstream.util.response :as sr]
    [superstring.core :as str]))

(def ^:const resource-name "Quota")

(def ^:const resource-tag (keyword (str (str/camel-case resource-name) "s")))

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "QuotaCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})

;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::quota/quota))
(defmethod crud/validate resource-uri
  [resource]
  (validate-fn resource))

;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl resource request))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

(defmethod crud/add resource-name
  [request]
  (add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))

(def edit-impl (std-crud/edit-fn resource-name))


(defmethod crud/edit resource-name
  [request]
  (edit-impl request))

(def delete-impl (std-crud/delete-fn resource-name))


(defmethod crud/delete resource-name
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))

(defmethod crud/query resource-name
  [request]
  (query-impl request))


;;
;; provide an action that allows the quota to be evaluated
;; returns the quota resource augmented with the keys
;; :currentAll (for all usage of the quota) and :currentUser
;; (for all usage of the quota by that user).
;;


(defmethod crud/set-operations resource-uri
  [{:keys [id resourceURI username] :as resource} request]
  (let [href (str id "/collect")
        collect-op {:rel (:collect c/action-uri) :href href}]
    (-> (crud/set-standard-operations resource request)
        (update-in [:operations] conj collect-op))))


(defmethod crud/do-action [resource-url "collect"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)]
      (-> (crud/retrieve-by-id-as-admin id)
          (quota-utils/collect request)
          sr/json-response))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::quota/quota))
