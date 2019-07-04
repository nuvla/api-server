(ns sixsq.nuvla.server.resources.data-record
  "
The `data-record` resource provides metadata for a particular data object.
(Although `data-record` resources can also be used independently of a
`data-object`.)

The schema for the this resource is open, allowing any information to be
associated with the data object. The only requirement is that keys must be
prefixed. The prefixes **must** be defined in a `data-record-key-prefix`
resource and the key itself **may** be described in a `data-record-key`
resource.
"
  (:require
    [clojure.string :as str]
    [ring.util.response :as r]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as sn]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.data-record :as data-record]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as sr]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; multimethods for validation and operations
;;

(defn key-prefix
  "Extracts the key's prefix if there is one. Returns nil otherwise."
  [k]
  (some->> k
           name
           (re-matches #"(.+):.*")
           second))


(defn valid-key-prefix?
  "If there is a prefix and it is NOT in the valid-prefixes set, return false.
   Otherwise return true."
  [valid-prefixes k]
  (if-let [prefix (key-prefix k)]
    (boolean (valid-prefixes prefix))
    true))


(defn- valid-attributes?
  [validator resource]
  (if-not (map? resource)
    true
    (and (every? validator (keys resource))
         (every? (partial valid-attributes? validator) (vals resource)))))


(defn- throw-wrong-namespace
  []
  (let [code     406
        msg      "resource uses keys with undefined prefixes"
        response (-> {:status code :message msg}
                     sr/json-response
                     (r/status code))]
    (throw (ex-info msg response))))


(defn- validate-attributes
  [resource]
  (let [valid-prefixes (sn/all-prefixes)
        validator      (partial valid-key-prefix? valid-prefixes)]
    (if (valid-attributes? validator resource)
      resource
      (throw-wrong-namespace))))


(def validate-fn (u/create-spec-validation-fn ::data-record/schema))
(defmethod crud/validate resource-type
  [resource]
  (-> resource
      validate-fn
      validate-attributes))


;;
;; multimethod for ACLs
;;

(defn create-acl [id]
  {:owners   ["group/nuvla-admin"]
   :edit-acl [id]})


(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


(defmethod crud/add-acl resource-type
  [{:keys [acl] :as resource} request]
  (if acl
    resource
    (let [user-id (:user-id (auth/current-authentication request))]
      (assoc resource :acl (create-acl user-id)))))


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


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::data-record/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::data-record/schema)
  (md/register resource-metadata))

