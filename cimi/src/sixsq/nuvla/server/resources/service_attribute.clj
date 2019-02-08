(ns sixsq.nuvla.server.resources.service-attribute
  "
A ServiceAttribute resource provides semantic information concerning an
attribute that appears in ServiceOffer resources. This resource is intended to
provide information that helps humans understand the information provided in a
ServiceOffer resource.

Currently, only an administrator can create, update, or delete
ServiceAttribute resources. These actions follow the standard CIMI patterns.
Most users will only search these resources and look at the details for a
particular ServiceAttribute resource.

Parameter | Required  | Description
--------- | --------  | -----------
name | true | short human-readable tag
description | true | longer human-readable description
prefix | true | namespace prefix
attributeName | true | name of the attribute itself
type | true | type of the attribute's value

Show all of the ServiceAttribute resources.

```shell
curl https://nuv.la/api/service-attribute
```
"
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.service-attribute-namespace :as san]
    [sixsq.nuvla.server.resources.spec.service-attribute :as sa]
    [sixsq.nuvla.util.response :as sr]
    [ring.util.response :as r])
  (:import
    [java.math BigInteger]
    [java.net URI URISyntaxException]
    [java.nio.charset Charset]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const collection-name "ServiceAttributeCollection")

(def ^:const resource-uri resource-type)

(def ^:const collection-uri collection-name)

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

;;
;; multimethods for validation and operations
;;

(defn validate-attribute-namespace
  [resource]
  (if ((san/all-prefixes) (:prefix resource))
    resource
    (let [code 406
          msg (str "resource attributes do not satisfy defined namespaces, prefix='"
                   (:prefix resource) "'")
          response (-> {:status code :message msg}
                       sr/json-response
                       (r/status code))]
      (throw (ex-info msg response)))))

(def validate-fn (u/create-spec-validation-fn ::sa/service-attribute))
(defmethod crud/validate resource-uri
  [resource]
  (-> resource
      validate-fn
      validate-attribute-namespace))

(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl resource request))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-uri))

(defn positive-biginteger
  [^bytes bs]
  (BigInteger. 1 bs))

(defn uri->id
  [^String uri]
  (if uri
    (try
      (-> uri
          (URI.)
          (.toASCIIString)
          (.getBytes (Charset/forName "US-ASCII"))
          (positive-biginteger)
          (.toString 16))
      (catch URISyntaxException _
        (throw (Exception. (str "invalid attribute URI: " uri)))))
    (throw (Exception. (str "attribute URI cannot be nil")))))

(defmethod crud/new-identifier resource-type
  [json resource-name]
  (let [new-id (str resource-type "/" (uri->id (str (:prefix json) ":" (:attributeName json))))]
    (assoc json :id new-id)))

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
  (std-crud/initialize resource-type ::sa/service-attribute))
