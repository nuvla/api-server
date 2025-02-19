(ns com.sixsq.nuvla.server.resources.common.utils
  "General utilities for dealing with resources."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.walk :as w]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.server.util.log :as logu]
    [com.sixsq.nuvla.server.util.response :as r]
    [com.sixsq.nuvla.server.util.time :as time]
    [expound.alpha :as expound])
  (:import
    (java.security MessageDigest SecureRandom)
    (java.util UUID)))


(def ^:const form-urlencoded "application/x-www-form-urlencoded")

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

(defn rand-uuid
  "Provides the string representation of a pseudo-random UUID."
  []
  (str (random-uuid)))


(defn from-data-uuid
  "Provides the string representation of a UUID generated from an input."
  [input]
  (str (UUID/nameUUIDFromBytes (.getBytes input "UTF-8"))))


(defn new-resource-id
  [resource-name]
  (str resource-name "/" (rand-uuid)))


(defn resource-id
  [resource-type uuid]
  (str resource-type "/" uuid))


(defn parse-id
  "Parses a resource id to provide a tuple of [resource-type uuid] for an id.
   For ids that don't have an identifier part (e.g. the cloud-entry-point), the
   document id will be nil. For any invalid argument, nil is returned."
  [id]
  (when (string? id)
    (let [[resource uuid] (str/split id #"/")]
      [resource uuid])))


(defn id->request-params
  "Creates the request path params map {:resource-name \"name\", :uuid
   \"uuid\"} for the given id. If the id isn't valid, then nil is returned."
  [id]
  (when-let [[resource-type uuid] (parse-id id)]
    (cond-> {:resource-name resource-type}                  ;; resource-name is historical
            uuid (assoc :uuid uuid))))


(defn request->resource-type
  "Extracts the resource type from a request map."
  [request]
  (get-in request [:params :resource-name]))


(defn request->resource-uuid
  "Extracts the resource uuid from a request map."
  [request]
  (get-in request [:params :uuid]))


(defn request->resource-id
  "Extracts the resource id from a request map."
  [request]
  (some->> (request->resource-uuid request)
           (resource-id (request->resource-type request))))


(defn id->resource-type
  "Parses a resource id to provide a tuple of [resource-type uuid] for an id.
   For ids that don't have an identifier part (e.g. the cloud-entry-point), the
   document id will be nil. For any invalid argument, nil is returned."
  [resource-id]
  (first (parse-id resource-id)))


(defn id->uuid
  "Parses a resource id to provide a tuple of [resource-type uuid] for an id.
   For ids that don't have an identifier part (e.g. the cloud-entry-point), the
   document id will be nil. For any invalid argument, nil is returned."
  [resource-id]
  (second (parse-id resource-id)))


(defn uuid->short-uuid
  [uuid]
  (-> uuid (str/split #"-") first))


(defn md5 [^String s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw       (.digest algorithm (.getBytes s))]
    (format "%032x" (BigInteger. 1 raw))))


(defn secure-rand-int
  "Returns a secure random integer between 0 (inclusive) and n (exclusive)."
  ([n]
   (let [random (SecureRandom.)]
     (.nextInt random n)))
  ([min max]
   (-> (- max min)
       (inc)
       (secure-rand-int)
       (+ min))))

(defn secure-rand-nth
  [coll]
  (nth coll (secure-rand-int (count coll))))


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
  "Strips non-removable attributes from the set. These include
  :id :created :updated :resource-type :acl"
  [s]
  (disj s :id :created :updated :resource-type :acl))

(defn strip-immutable-keys
  [s ks]
  (apply disj s ks))

(defn merge-resource
  [resource {:keys [body] :as _request} immutable-attributes]
  (let [new-body (apply dissoc body immutable-attributes)]
    (merge resource new-body (select-keys resource immutable-attributes))))


(defn update-timestamps
  "Sets the updated attribute and optionally the created attribute
   in the request.  The created attribute is only set if the existing value
   is missing or evaluates to false."
  [data]
  (let [updated (time/now-str)
        created (or (:created data) updated)]
    (assoc data :created created :updated updated)))


(defn set-created-by
  "Sets the created by attribute."
  [data request]
  (assoc data :created-by (or (auth/current-user-id request)
                              "group/nuvla-anon")))


(defn set-updated-by
  "Sets the updated by attribute."
  [data request]
  (assoc data :updated-by (or (auth/current-user-id request)
                              "group/nuvla-anon")))


(defn ttl->timestamp
  "Converts a Time to Live (TTL) value in seconds to timestamp string. The
   argument must be an integer value."
  [ttl]
  (time/to-str (time/from-now ttl :seconds)))


(defn expired?
  "This will return true if the given date (as a string) represents a moment
   of time in the past. Returns false otherwise."
  [expiry]
  (boolean (and expiry (time/before? (time/parse-date expiry) (time/now)))))


(def not-expired? (complement expired?))


(defn select-desc-keys
  "Selects the common attributes that are related to the description of the
   resource, namely 'name', 'description', 'tags', 'parent', and 'acl'."
  [m]
  (select-keys m #{:name :description :tags :parent :acl}))


(defn create-generic-spec-validation-fn
  ([spec error-fn]
   (create-generic-spec-validation-fn spec identity error-fn))
  ([spec accessor-fn error-fn]
   (let [ok?     (partial s/valid? spec)
         explain (partial expound/expound-str spec)]
     (fn [value]
       (let [checked-value (accessor-fn value)]
         (if (ok? checked-value)
           value
           (error-fn checked-value (explain checked-value))))))))

(defn create-spec-validation-fn
  "Creates a validation function that compares a resource against the
   given schema.  The generated function raises an exception with the
   violations of the schema and a 400 ring response. If everything's
   OK, then the resource itself is returned."
  [spec]
  (create-generic-spec-validation-fn
    spec
    (fn [resource spec-error]
      (logu/log-and-throw-400
        (str "resource "
             (some-> resource :id (str " "))
             "does not satisfy defined schema:\n"
             spec-error)))))

(defn create-spec-validation-request-body-fn
  [spec]
  (create-generic-spec-validation-fn
    spec
    :body
    (fn [_body spec-error]
      (logu/log-and-throw-400
        (str "request body does not satisfy defined schema:\n"
             spec-error)))))


(defn get-op
  "Get the operation href from the resources operations value."
  [{:keys [operations]} op]
  (->> operations
       (map (juxt :rel :href))
       (filter (fn [[rel _]] (= rel (name op))))
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


(defn operation-map
  "Provides the operation map for the given href and operation."
  [href op-kw-or-name]
  {:rel  (name op-kw-or-name)
   :href href})


(defn action-map
  "Provides the operation map for an action, which always has a relative path
   to the resource's id."
  [id op-kw-or-name]
  (let [href (str id "/" (name op-kw-or-name))]
    (operation-map href op-kw-or-name)))


(defn convert-form
  "Allow form encoded data to be supplied for a session. This is required to
   support external authentication methods triggered via a 'submit' button in
   an HTML form. This takes the flat list of form parameters, keywordizes the
   keys, and adds the parent :sessionTemplate key."
  [tpl form-data]
  {tpl (w/keywordize-keys form-data)})


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


(defn delete-attributes
  [{:keys [acl] :as current}
   {{select :select} :cimi-params :as request}
   immutable-keys]
  (let [immutable-keys (if (a/is-admin-request? request) [] immutable-keys)
        rights         (a/extract-rights (auth/current-authentication request) acl)
        dissoc-keys    (-> (map keyword select)
                           (a/editable-keys rights)
                           strip-select-from-mandatory-attrs
                           (strip-immutable-keys immutable-keys))]
    (apply dissoc current dissoc-keys)))


(defn merge-body
  [{:keys [acl] :as resource}
   {body :body :as request}
   immutable-keys]
  (let [immutable-keys (if (a/is-admin-request? request) [] immutable-keys)
        rights         (a/extract-rights (auth/current-authentication request) acl)
        editable-body  (-> body
                           keys
                           (a/editable-keys rights)
                           (strip-immutable-keys immutable-keys)
                           (->> (select-keys body)))]
    (merge resource editable-body)))


(defn is-state-within?
  [states resource]
  (contains? (set states) (:state resource)))


(def is-state-besides? (complement is-state-within?))


(defn is-state?
  [state resource]
  (= (:state resource) state))

(defn throw-cannot-do
  [{:keys [id] :as resource} pred error-msg]
  (if (pred resource)
    resource
    (throw (r/ex-response error-msg 409 id))))

(defn throw-cannot-do-action
  [{:keys [id] :as resource} pred action]
  (throw-cannot-do resource pred (format "operation '%s' not allowed on %s" action id)))

(defn throw-cannot-do-action-invalid-state
  [{:keys [id state] :as resource} pred action]
  (throw-cannot-do resource pred (format "Action %s cannot be called on %s in state %s!" action id state)))

(defn filter-eq-vals
  [attribute vals]
  (str attribute "=" (vec vals)))

(defn stringify-keys
  [m]
  (let [f (fn [[k v]] (if (keyword? k) [(if-let [ns (namespace k)]
                                          (str ns "/" (name k))
                                          (name k)) v] [k v]))]
    (w/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))
