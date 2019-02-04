(ns com.sixsq.slipstream.ssclj.resources.virtual-machine
  "
These resources provide information for all active virtual machines on the
underlying cloud infrastructures. Together they constitute the current global
state of compute resources managed by SlipStream.

You must be authenticated with the SlipStream server to access the
VirtualMachine collection and resources.

> WARNING: SlipStream handles the lifecycle of these resources, creating,
updating, and deleting them as it monitors the associated virtual machines.
Users typically are only interested in viewing and querying these resources.

## List Virtual Machines

```shell
curl https://nuv.la/api/virtual-machine -b ~/cookies -D -
```

```http
HTTP/2 200
server: nginx
date: Wed, 17 Oct 2018 08:36:24 GMT
content-type: application/json
content-length: 3254718
vary: Accept-Encoding
strict-transport-security: max-age=31536000
```

```json
{
  \"resourceURI\" : \"http://sixsq.com/slipstream/1/VirtualMachineCollection\",
  \"id\" : \"virtual-machine\",
  \"count\" : 870,

  ...

  \"virtualMachines\" : [ {
    \"connector\" : {
      \"href\" : \"connector/exoscale-de-fra\"
    },
    \"ip\" : \"194.182.170.118\",
    \"credentials\" : [ {
      \"href\" : \"credential/7353af45-cd25-4bb0-9444-ee1935aa8a81\"
    } ],
    \"updated\" : \"2018-10-17T08:28:59.345Z\",
    \"billable\" : true,
    \"created\" : \"2018-10-15T13:54:48.539Z\",
    \"state\" : \"running\",
    \"currency\" : \"EUR\",
    \"instanceID\" : \"0781484e-c82a-4522-b339-386e12c9e8ce\",
    \"id\" : \"virtual-machine/a5d482e1-1fc0-33b7-b3b0-78fc4a5d08b2\",
    ...
    }]
    ...
```

Listing the VirtualMachine resources follows the standard CIMI pattern, using
an HTTP GET request on the collection URL.

The example command returns the HTTP headers of the request and then the full
list of Virtual Machine resources as a JSON document. The returned JSON
document contains some general information concerning, for example, the total
number of documents ('count' key), followed by the list of VirtualMachine
resource under the 'virtualMachines' key.

## Filtering Virtual Machines

The full [CIMI filtering syntax](#resource-selection) (with SlipStream
extensions) can be used to find a subset of the VirtualMachine resources. For
example, the filter terms:

 - `connector/href=\"connector/exoscale-de-fra\"`
 - `state=\"running\"`

would limit the response to only virtual machines in a running state on the
'exoscale-de-fra' cloud. Other query parameters, can also be used, for example
`$last=0` to just return the global count information.

```shell
curl 'https://nuv.la/api/virtual-machine?$filter=connector/href=\"connector/exoscale-de-fra\"%20and%20state=\"running\"&$last=0' -b ~/cookies -D -
```

```json
{
  \"count\" : 351,
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
  \"resourceURI\" : \"http://sixsq.com/slipstream/1/VirtualMachineCollection\",
  \"id\" : \"virtual-machine\",
  \"operations\" : [ {
    \"rel\" : \"add\",
    \"href\" : \"virtual-machine\"
  } ],
  \"virtualMachines\" : [ ]
}
```
"
  (:require
    [clojure.string :as str]
    [com.sixsq.slipstream.auth.acl :as a]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.spec.virtual-machine :as vm]))

(def ^:const resource-tag :virtualMachines)

(def ^:const resource-name "VirtualMachine")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "VirtualMachineCollection")

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

(defn create-acl [id]
  {:owner {:principal "ADMIN"
           :type      "ROLE"}
   :rules [{:principal id
            :type      "USER"
            :right     "VIEW"}]})

;;
;; set the resource identifier to "virtual-machine/uuid(connector-href, instanceID)"
;;
(defmethod crud/new-identifier resource-name [json resource-name]
  (let [connector-href (get-in json [:connector :href])
        instanceID (get json :instanceID)
        id (u/from-data-uuid (str connector-href instanceID))]
    (assoc json :id (str resource-url "/" id))))

(def validate-fn (u/create-spec-validation-fn ::vm/virtual-machine))
(defmethod crud/validate
  resource-uri
  [resource]
  (validate-fn resource))

(defmethod crud/add-acl resource-uri
  [{:keys [acl] :as resource} request]
  (if acl
    resource
    (let [user-id (:identity (a/current-authentication request))
          run-owner (some-> request
                            (get-in [:body :deployment :user :href])
                            (str/replace #"^user/" ""))]
      (if run-owner
        (assoc resource :acl (create-acl run-owner))
        (assoc resource :acl (create-acl user-id))))))

;;
;; CRUD operations
;;
(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))
(defmethod crud/add resource-name
  [{{:keys [serviceOffer]} :body :as request}]
  ;;Create a `currency` attribute when it is defined in the linked serviceOffer
  (if (:price:currency serviceOffer)
    (add-impl (assoc-in request [:body :currency] (:price:currency serviceOffer)))
    (add-impl request)))

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
  (std-crud/initialize resource-url ::vm/virtual-machine))
