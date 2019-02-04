(ns com.sixsq.slipstream.ssclj.resources.metering
  "
The Metering resources are snapshots of the monitoring information
(VirtualMachine and StorageBucket resources) that are taken every minute to
provide historical usage information. These resources can be queried to provide
aggregate usage information for a given time period, specific groups or users,
etc.

> WARNING: SlipStream handles the lifecycle of these resources, creating them
with each periodic snapshot. Users typically are only interested in viewing,
querying, and aggregating values for these resources.

## Filtering and Aggregation

Typically you will query these resources by providing a time window, a
user/group, and the value(s) to aggregate. To filter by the date and user, a
`$filter` expression similar to the following can be used:

`created<\"now-1d\" and created>\"2018-09-13T00:00:00Z\"`

Explicit dates can be used or relative times compared to 'now'. The support
for 'now' expressions is an extension to the CIMI standard. The 'now'
expression syntax comes directly from the underlying Elasticsearch database.

You will almost never be interested in the individual metering resources. **As
there are an extremely large number in any time window, the list can be
suppressed by providing the query parameter `$last=0`.** With the general
information and any aggregation information will be returned, but none of the
selected resources.

You can review the [aggregation documentation](#resource-selection) to see
what aggregations are supported. In the context of accounting, the 'count',
'avg', and 'sum' aggregations are usually the most useful. To sum the money
spent on the resources (assuming that prices have been defined for the cloud),
you can use a query expression like the following:

`$aggregation=sum:price`

to calculate the total cost of the selected resources. Multiple aggregations
can be specified either as a multiple parameters or as a comma-separated value.
The requested information will be returned in the 'aggregations' key of the
response.

The example shows a full example and the JSON response.

Provide the average number of CPU cores and total cost for the 'user/test'
user for the given time period.

```shell
curl 'https://nuv.la/api/metering?$last=0&deployment/user!='user/test'&$aggregation=sum:price,avg:serviceOffer/resource:vpu&$filter=created<\"now\"%20and%20created>\"2018-09-13T00:00:00Z\"%20and%20deployment/user/href=\"user/test\"' \\
    -b ~/cookies -D -
```

```json
{
  \"count\" : 45083692,
  \"aggregations\" : {
    \"avg:serviceOffer/resource:vcpu\" : {
      \"value\" : 6.730052249501845
    },
    \"sum:price\" : {
      \"value\" : 187186.03962752942
    }
  },
  \"acl\" : {
    \"owner\" : {
      \"principal\" : \"ADMIN\",
      \"type\" : \"ROLE\"
    },
    \"rules\" : [ {
      \"principal\" : \"ADMIN\",
      \"type\" : \"ROLE\",
      \"right\" : \"MODIFY\"
    }, {
      \"principal\" : \"USER\",
      \"type\" : \"ROLE\",
      \"right\" : \"VIEW\"
    } ]
  },
  \"resourceURI\" : \"http://sixsq.com/slipstream/1/MeteringCollection\",
  \"id\" : \"metering\",
  \"operations\" : [ {
    \"rel\" : \"add\",
    \"href\" : \"metering\"
  } ],
  \"meterings\" : [ ]
}
```
"
  (:require
    [com.sixsq.slipstream.auth.acl :as a]

    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.spec.metering :as metering]
    [superstring.core :as str]))

(def ^:const resource-name "Metering")

(def ^:const resource-tag (keyword (str (str/camel-case resource-name) "s")))

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "MeteringCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

;; only authenticated users can view and create credentials
(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})

;;
;; multimethod for ACLs
;;

(defn create-acl
  [id]
  {:owner {:principal "ADMIN"
           :type      "ROLE"}
   :rules [{:principal id
            :type      "USER"
            :right     "VIEW"}]})

(def validate-fn (u/create-spec-validation-fn ::metering/metering))
(defmethod crud/validate
  resource-uri
  [resource]
  (validate-fn resource))

(defmethod crud/add-acl resource-uri
  [{:keys [acl] :as resource} request]
  (if acl
    resource
    (let [user-id (:identity (a/current-authentication request))
          run-owner (subs (-> request
                              :body
                              :run
                              :user
                              :href
                              )
                          (count "/user"))]
      (if run-owner
        (assoc resource :acl (create-acl run-owner))
        (assoc resource :acl (create-acl user-id))))))

;;
;; CRUD operations
;;
(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))
(defmethod crud/add resource-name
  [request]
  (add-impl request))

(def edit-impl (std-crud/edit-fn resource-name))
(defmethod crud/edit resource-name
  [request]
  (edit-impl request))

(def retrieve-impl (std-crud/retrieve-fn resource-name))
(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))

(def delete-impl (std-crud/delete-fn resource-name))
(defmethod crud/delete resource-name
  [request]
  (delete-impl request))

(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))
(defmethod crud/query resource-name
  [request]
  (query-impl request))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::metering/metering))
