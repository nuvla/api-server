(ns sixsq.nuvla.server.resources.common.utils
  "General utilities for dealing with resources."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [expound.alpha :as expound]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.time :as time])
  (:import
    (java.security MessageDigest)
    (java.util UUID)))


;;
;; resource type from namespace
;;

(defn ns->type
  [ns]
  (-> ns str (str/split #"\.") last))


(defn ns->collection-type
  [ns]
  (str (ns->type ns) "-collection"))


(defn ns->create-type
  [ns]
  (str (ns->type ns) "-create"))


;;
;; check resource category
;;

(defn is-collection?
  [resource-type]
  (and (string? resource-type)
       (.endsWith resource-type "-collection")))


;;
;; resource ID utilities
;;

(defn random-uuid
  "Provides the string representation of a pseudo-random UUID."
  []
  (str (UUID/randomUUID)))


(defn from-data-uuid
  "Provides the string representation of a UUID generated from an input."
  [input]
  (str (UUID/nameUUIDFromBytes (.getBytes input "UTF-8"))))


(defn new-resource-id
  [resource-name]
  (str resource-name "/" (random-uuid)))


(defn split-resource-id
  "Provide a tuple of [type docid] for a resource ID. For IDs that don't have
   an identifier part (e.g. the cloud-entry-point), the document ID will be nil."
  [id]
  (let [[type docid] (str/split id #"/")]
    [type docid]))


(defn resource-name
  [resource-id]
  (first (split-resource-id resource-id)))


(defn document-id
  [resource-id]
  (second (split-resource-id resource-id)))


(defn md5 [^String s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes s))]
    (format "%032x" (BigInteger. 1 raw))))


;;
;; utilities for handling common attributes
;;

(defn strip-common-attrs
  "Strips all common resource attributes from the map."
  [m]
  (dissoc m :id :name :description :created :updated :tags))


(defn strip-service-attrs
  "Strips common attributes from the map whose values are controlled
   entirely by the service.  These include :id, :created, :updated,
   :resource-type, and :operations."
  [m]
  (dissoc m :id :created :updated :resource-type :operations))


(defn strip-select-from-mandatory-attrs
  "Strips non removable attributes from the set. These include
  :id :created :updated :resource-type :acl"
  [s]
  (disj s :id :created :updated :resource-type :acl))


(defn update-timestamps
  "Sets the updated attribute and optionally the created attribute
   in the request.  The created attribute is only set if the existing value
   is missing or evaluates to false."
  [data]
  (let [updated (time/now-str)
        created (or (:created data) updated)]
    (assoc data :created created :updated updated)))


(defn ttl->timestamp
  "Converts a Time to Live (TTL) value in seconds to timestamp string. The
   argument must be an integer value."
  [ttl]
  (time/to-str (time/from-now ttl :seconds)))


(defn expired?
  "This will return true if the given date (as a string) represents a moment
   of time in the past. Returns false otherwise."
  [expiry]
  (boolean (and expiry (time/before? (time/date-from-str expiry) (time/now)))))


(def not-expired? (complement expired?))


(defn select-desc-keys
  "Selects the common attributes that are related to the description of the
   resource, namely 'name', 'description', and tags."
  [m]
  (select-keys m #{:name :description :tags}))


(defn create-spec-validation-fn
  "Creates a validation function that compares a resource against the
   given schema.  The generated function raises an exception with the
   violations of the schema and a 400 ring response. If everything's
   OK, then the resource itself is returned."
  [spec]
  (let [ok? (partial s/valid? spec)
        explain (partial expound/expound-str spec)]
    (fn [resource]
      (if-not (ok? resource)
        (logu/log-and-throw-400 (str "resource does not satisfy defined schema:\n" (explain resource)))
        resource))))


(defn get-op
  "Get the operation href from the resources operations value."
  [{:keys [operations]} op]
  (->> operations
       (map (juxt :rel :href))
       (filter (fn [[rel _]] (= rel op)))
       first
       second))


(defn remove-in
  "Removes the set of `rm-set` elements from a list under key `k` in the map `m`."
  [m k rm-set]
  (update-in m [k] #(vec (remove rm-set %))))


(defn remove-req
  "Removes required elements defined in `specs` set from `keys-spec` spec."
  [keys-spec specs]
  (remove-in keys-spec :req-un specs))
