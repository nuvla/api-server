(ns com.sixsq.slipstream.ssclj.resources.common.utils
  "General utilities for dealing with resources."
  (:require
    [clj-time.coerce :as c]
    [clj-time.core :as time]
    [clj-time.format :as time-fmt]
    [clojure.spec.alpha :as s]
    [clojure.walk :as walk]
    [com.sixsq.slipstream.ssclj.util.log :as logu]
    [expound.alpha :as expound]
    [superstring.core :as str])
  (:import
    (java.security MessageDigest)
    (java.util Date UUID)
    (org.joda.time DateTime)))


(def ^:const form-urlencoded "application/x-www-form-urlencoded")

;; NOTE: this cannot be replaced with s/lisp-case because it
;; will treat a '/' in a resource name as a word separator.
(defn de-camelcase [s]
  (if s
    (str/join "-" (map str/lower-case (str/split s #"(?=[A-Z])")))
    ""))

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
   an identifier part (e.g. the CloudEntryPoint), the document ID will be nil."
  [id]
  (let [[type docid] (str/split id #"/")]
    [type docid]))

(defn resource-name
  [resource-id]
  (first (split-resource-id resource-id)))

(defn document-id
  [resource-id]
  (second (split-resource-id resource-id)))


(defn cimi-collection? [resourceURI]
  (and (instance? String resourceURI)
       (.endsWith ^String resourceURI "Collection")))

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
  (dissoc m :id :name :description :created :updated :properties))

(defn strip-service-attrs
  "Strips common attributes from the map whose values are controlled
   entirely by the service.  These include :id, :created, :updated,
   :resourceURI, and :operations."
  [m]
  (dissoc m :id :created :updated :resourceURI :operations))

(defn strip-select-from-mandatory-attrs
  "Strips non removable attributes from the set. These include
  :id :created :updated :resourceURI :acl"
  [s]
  (disj s :id :created :updated :resourceURI :acl))

(defn unparse-timestamp-datetime
  "Returns the string representation of the given timestamp."
  [^DateTime timestamp]
  (try
    (time-fmt/unparse (:date-time time-fmt/formatters) timestamp)
    (catch Exception _
      nil)))

(defn unparse-timestamp-date
  "Returns the string representation of the given timestamp."
  [^Date timestamp]
  (try
    (unparse-timestamp-datetime (c/from-date timestamp))
    (catch Exception _
      nil)))

(defn as-datetime
  "Tries to parse the given string as a DateTime value.  Returns the DateTime
   instance on success and nil on failure."
  [data]
  (when (string? data)
    (try
      (time-fmt/parse (:date-time time-fmt/formatters) data)
      (catch Exception _
        nil))))

(defn as-text
  "A function that marks a field as being parsable text rather than a keyword."
  [data]
  (s/and string? (complement str/blank?)))

(defn update-timestamps
  "Sets the updated attribute and optionally the created attribute
   in the request.  The created attribute is only set if the existing value
   is missing or evaluates to false."
  [data]
  (let [updated (unparse-timestamp-datetime (time/now))
        created (or (:created data) updated)]
    (assoc data :created created :updated updated)))

(defn ttl->timestamp
  "Converts a Time to Live (TTL) value in seconds to timestamp string. The
   argument must be an integer value."
  [ttl]
  (unparse-timestamp-datetime (time/from-now (time/seconds ttl))))

(defn expired?
  "This will return true if the given date (as a string) represents a moment
   of time in the past.  Returns false otherwise."
  [expiry]
  (boolean (and expiry (time/before? (as-datetime expiry) (time/now)))))

(def not-expired? (complement expired?))

(defn select-desc-keys
  "Selects the common attributes that are related to the description of the
   resource, namely 'name', 'description', and properties."
  [m]
  (select-keys m #{:name :description :properties}))

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
       (filter (fn [[rel _]] (.endsWith rel op)))
       first
       second))


(defn into-vec-without-nil
  [op xs]
  (when (->> xs
             (remove nil?)
             seq)
    (->> (into [op] xs)
         (remove nil?)
         vec)))

(defn- lisp-cased?
  [s]
  (re-matches #"[a-z]+(-[a-z]+)*" s))

(defn lisp-to-camelcase
  "Converts s to CamelCase format. If the argument is not lisp-cased, an empty
  string is returned."
  [s]
  (if (lisp-cased? s)
    (str/pascal-case s)
    ""))

(defn map-multi-line
  [m]
  (str "\n" (clojure.pprint/write m :stream nil :right-margin 50)))


(defn remove-in
  "Removes the set of `rm-set` elements from a list under key `k` in the map `m`."
  [m k rm-set]
  (update-in m [k] #(vec (remove rm-set %))))

(defn remove-req
  "Removes required elements defined in `specs` set from `keys-spec` spec."
  [keys-spec specs]
  (remove-in keys-spec :req-un specs))

(defn remove-opt
  "Removes optional elements defined in `specs` set from `keys-spec` spec."
  [keys-spec specs]
  (remove-in keys-spec :opt-un specs))

(defn convert-form
  "Allow form encoded data to be supplied for a session. This is required to
   support external authentication methods triggered via a 'submit' button in
   an HTML form. This takes the flat list of form parameters, keywordizes the
   keys, and adds the parent :sessionTemplate key."
  [tpl form-data]
  {tpl (walk/keywordize-keys form-data)})

(defn is-content-type?
  "Checks if the given header name is 'content-type' in various forms."
  [k]
  (try
    (= :content-type (-> k name str/lower-case keyword))
    (catch Exception _
      false)))

(defn is-form?
  "Checks the headers to see if the content type is
   application/x-www-form-urlencoded. Converts the header names to lowercase
   and keywordizes the result to collect the various header name variants."
  [headers]
  (->> headers
       (filter #(is-content-type? (first %)))
       first
       second
       (= form-urlencoded)))
