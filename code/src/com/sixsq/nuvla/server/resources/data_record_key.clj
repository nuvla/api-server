(ns com.sixsq.nuvla.server.resources.data-record-key
  "
A `data-record-key` resource provides semantic information concerning a key
that appears in `data-record` resources. This resource is intended to provide
information that helps humans understand the information provided in a
`data-record` resource.

The `name` and `description` attributes are required for this resource.
"
  (:require
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.data-record-key-prefix :as san]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.data-record-key :as data-record-key]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as sr]
    [ring.util.response :as r])
  (:import
    [java.math BigInteger]
    [java.net URI URISyntaxException]
    [java.nio.charset Charset]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; multimethods for validation and operations
;;

(defn validate-attribute-namespace
  [resource]
  (if ((san/all-prefixes) (:prefix resource))
    resource
    (let [code     406
          msg      (str "resource attributes do not satisfy defined namespaces, prefix='"
                        (:prefix resource) "'")
          response (-> {:status code :message msg}
                       sr/json-response
                       (r/status code))]
      (throw (ex-info msg response)))))

(def validate-fn (u/create-spec-validation-fn ::data-record-key/schema))
(defmethod crud/validate resource-type
  [resource]
  (-> resource
      validate-fn
      validate-attribute-namespace))

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

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
  [json _resource-name]
  (let [new-id (str resource-type "/" (uri->id (str (:prefix json) ":" (:key json))))]
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

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))

(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-type ::data-record-key/schema)
  (md/register (gen-md/generate-metadata ::ns ::data-record-key/schema)))
