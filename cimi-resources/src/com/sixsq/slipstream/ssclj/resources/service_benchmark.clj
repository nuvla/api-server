(ns com.sixsq.slipstream.ssclj.resources.service-benchmark
  "
The ServiceBenchmark resource allows performance and other service metrics to
be posted about one or more Service Offers. Such information can be used to
make informed decisions about which cloud resources to provision for a
particular application.

Like for the ServiceOffer, the schema is open, allowing publication of any
benchmarks. The only requirement is that these attributes must be namespaced.
The namespaces **must** be defined in a ServiceAttributeNamespace resources and
the attribute itself **may** be described in a ServiceAttribute resource.

An example service benchmark showing the Whetstone and Dhrystone benchmarks
for an instance at Exoscale.

```json

{
  \"id\": \"service-benchmark/25de61b0-0a54-41ab-b8da-d2846194da13\",
  \"resourceURI\": \"http://sixsq.com/slipstream/1/ServiceBenchmark\",
  \"created\": \"2017-10-03T19:36:16.962Z\",
  \"updated\": \"2017-10-03T19:36:16.962Z\",

  \"credentials\": [
    {
      \"href\": \"credential/51f3ed99-c46e-43c8-9926-b8b64bcb86dd\"
    }
  ],
  \"serviceOffer\": {
    \"href\": \"service-offer/92fee6cb-86c2-4fff-be5e-c87e6b27cf37\",
    \"connector\": {
      \"href\": \"exoscale-ch-gva\"
    },
    \"resource:ram\": 512,
    \"resource:vcpu\": 1,
    \"resource:disk\": 10,
    \"resource:instanceType\": \"Micro\"
  },

  \"whetstone:score\": 3997.3,
  \"whetstone:units\": \"MWIPS\",
  \"whetstone:freetext\": \"Executed by a Nuvla App\",

  \"dhrystone:score\": 33322937.2,
  \"dhrystone:units\": \"lps\",
  \"dhrystone:freetext\": \"Executed by a Nuvla App\",

  \"acl\" : {\"...\" : \"...\"},
  \"operations\": [ \"...\" ]
}
```

Show all of the ServiceBenchmark resources from the 'exoscale-ch-gva'. With an
average over the Whetstone score.

```shell
curl 'https://nuv.la/api/service-benchmark?$filter=serviceOffer/connector/href=\"exoscale-ch-gva\"&$aggregation=avg:whetstone:score'
```

```json
{
  \"id\" : \"service-benchmark\",
  \"resourceURI\" : \"http://sixsq.com/slipstream/1/ServiceBenchmarkCollection\",

  \"count\" : 25,
  \"aggregations\" : {
    \"avg:whetstone:score\" : {
      \"value\" : 4061.52400390625
    }
  },

  \"serviceBenchmarks\" : [ { \"...\" : \"...\" } ]
}
```
"
  (:require
    [com.sixsq.slipstream.auth.acl :as a]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.service-attribute-namespace :as sn]
    [com.sixsq.slipstream.ssclj.resources.service-catalog.utils :as sc]
    [com.sixsq.slipstream.ssclj.resources.spec.service-benchmark :as sb]
    [superstring.core :as str]))


(def ^:const resource-name "ServiceBenchmark")

(def ^:const resource-tag (keyword (str (str/camel-case resource-name) "s")))

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "ServiceBenchmarkCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})


;;
;; multimethods for validation and operations
;;

(defn- validate-attributes
  [resource]
  (let [valid-prefixes (sn/all-prefixes)
        resource-payload (dissoc resource :acl :id :resourceURI :name :description
                                 :created :updated :properties :operations :credentials :serviceOffer)
        validator (partial sc/valid-attribute-name? valid-prefixes)]
    (if (sc/valid-attributes? validator resource-payload)
      resource
      (sc/throw-wrong-namespace))))

;
(def validate-fn (u/create-spec-validation-fn ::sb/service-benchmark))
(defmethod crud/validate resource-uri
  [resource]
  (-> resource
      validate-fn
      validate-attributes))

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
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::sb/service-benchmark))
