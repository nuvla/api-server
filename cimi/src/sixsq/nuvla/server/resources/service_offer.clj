(ns sixsq.nuvla.server.resources.service-offer
  "
The ServiceOffer resource is the primary resource in the Service Catalog. Each
offer describes a specific resource offer from a cloud service provider. The
specific offer for a virtual machine would typically describe the offer's CPU,
RAM, and disk resources, the geographical location, and the price.

In the example offer from the Open Telekom Cloud (OTC), you can see three
types of information: metadata (e.g. id, name), the cloud connector (in
connector/href), and the cloud characteristics (e.g. price:unitCost,
resource:country).

The schema for the cloud characteristics is open, allowing cloud providers to
supply any information that is useful for users. The only requirement is that
these attributes must be namespaced. The namespaces **must** be defined in a
ServiceAttributeNamespace resources and the attribute itself **may** be
described in a ServiceAttribute resource.

Currently, only SlipStream administrators can add, update, or delete
ServiceOffer resources. The standard CIMI patterns for these actions apply to
these resources. Most users will search the ServiceOffer entries by using a
filter expression on the ServiceOffer collection. The example searches for all
ServiceOffers from the 'exoscale-ch-gva' cloud with more than 4096 MB of RAM.


An example service offer from the Open Telekom Cloud (OTC).

```json
{
  \"id\" : \"service-offer/0b2a46dd-f8c8-4420-a851-519daa581a1d\",
  \"name\" : \"(4/4096/800 c1.xlarge windows) [DE]\",
  \"description\" : \"VM (standard) with 4 vCPU, 4096 MiB RAM, 800 GiB root disk, windows [DE] (c1.xlarge)\",
  \"created\" : \"2017-06-26T11:13:42.883Z\",
  \"updated\" : \"2017-07-05T15:17:55.005Z\",

  \"connector\" : {
    \"href\" : \"open-telekom-de1\"
  },

  \"price:billingPeriodCode\" : \"MIN\",
  \"price:billingUnitCode\" : \"HUR\",
  \"price:currency\" : \"EUR\",
  \"price:freeUnits\" : 0,
  \"price:unitCost\" : 0.38711111111111113,
  \"price:unitCode\" : \"C62\",

  \"resource:class\" : \"standard\",
  \"resource:country\" : \"DE\",
  \"resource:diskType\" : \"SATA\",
  \"resource:instanceType\" : \"c1.xlarge\",
  \"resource:operatingSystem\" : \"windows\",
  \"resource:platform\" : \"openstack\",
  \"resource:ram\" : 4096,
  \"resource:vcpu\" : 4,

  \"otc:instanceType\" : \"c1.xlarge\",
  \"otc:flavorPurpose\" : \"Compute I Nr. 3\",

  \"acl\" : {\"...\" : \"...\"}
}
```

To show all of the ServiceOffer resources from the 'exoscale-ch-gva' cloud
that provide more than 4096 MB of RAM. Be sure to escape the dollar sign in the
`filter` query parameter!

```shell
curl 'https://nuv.la/api/service-offer?filter=connector/href=\"exoscale-ch-gva\" and resource:ram>4096'
```
"
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.service-attribute-namespace :as sn]
    [sixsq.nuvla.server.resources.spec.service-offer :as so]
    [sixsq.nuvla.util.response :as sr]
    [ring.util.response :as r]
    [clojure.string :as str]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const collection-name (u/ns->collection-type *ns*))

(def ^:const collection-uri collection-name)

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

;;
;; multimethods for validation and operations
;;

(defn valid-attribute-name?
  [valid-prefixes attr-name]
  (let [[ns _] (str/split (name attr-name) #":")]
    (valid-prefixes ns)))

(defn- valid-attributes?
  [validator resource]
  (if-not (map? resource)
    true
    (and (every? validator (keys resource))
         (every? (partial valid-attributes? validator) (vals resource)))))

(defn- throw-wrong-namespace
  []
  (let [code 406
        msg "resource attributes do not satisfy defined namespaces"
        response (-> {:status code :message msg}
                     sr/json-response
                     (r/status code))]
    (throw (ex-info msg response))))

(defn- validate-attributes
  [resource]
  (let [valid-prefixes (sn/all-prefixes)
        resource-payload (dissoc resource :acl :id :resource-type :name :description
                                 :created :updated :tags :operations :connector)
        validator (partial valid-attribute-name? valid-prefixes)]
    (if (valid-attributes? validator resource-payload)
      resource
      (throw-wrong-namespace))))

(def validate-fn (u/create-spec-validation-fn ::so/service-offer))
(defmethod crud/validate resource-type
  [resource]
  (-> resource
      validate-fn
      validate-attributes))

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [request]
  (add-impl request))

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


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-type ::so/service-offer))
