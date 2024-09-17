(ns com.sixsq.nuvla.server.resources.common.std-crud
  "Standard CRUD functions for resources."
  (:require
    [clojure.stacktrace :as st]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [clojure.walk :as w]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [clj-json-patch.core :as json-patch]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.middleware.cimi-params.impl :as impl]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.state-machine :as sm]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.spec.acl-collection :as acl-collection]
    [com.sixsq.nuvla.server.util.response :as r]))


(def validate-collection-acl (u/create-spec-validation-fn ::acl-collection/acl))


(defn pass-through
  [resource _request]
  resource)

(defn add-fn
  [resource-name collection-acl resource-uri & {:keys [pre-validate-hook
                                                       options]
                                                :or   {pre-validate-hook pass-through}}]
  (validate-collection-acl collection-acl)
  (fn [{:keys [body] :as request}]
    (a/throw-cannot-add collection-acl request)
    (-> body
        u/strip-service-attrs
        (crud/new-identifier resource-name)
        (assoc :resource-type resource-uri)
        sm/initialize
        u/update-timestamps
        (u/set-created-by request)
        (crud/add-acl request)
        (pre-validate-hook request)
        crud/validate
        (db/add options))))

(defn select-keys-resource
  [resource {{:keys [select]} :cimi-params :as _request}]
  (if select
    (select-keys resource (conj (map keyword select) :id))
    resource))

(defn retrieve-fn
  [resource-name]
  (fn [{{uuid :uuid} :params :as request}]
    (try
      (-> (str resource-name "/" uuid)
          (crud/retrieve-by-id request)
          (crud/set-operations request)
          (a/select-viewable-keys request)
          (select-keys-resource request)
          r/json-response)
      (catch Exception e
        (or (ex-data e) (throw e))))))

(defn json-safe-patch
  [obj patches]
  (try
    (if-let [result (json-patch/patch obj patches)]
      result
      (throw (Exception. "Patch interpretation failed!")))
    (catch Exception e
      (log/error "ex-message:" (ex-message e) "ex-data:" (ex-data e) "exception:" e "resource:" (prn-str obj) "patches:" (prn-str (vec patches)))
      (throw (r/ex-bad-request (str "Json patch exception: " (ex-message e)))))))

(defn- json-patch-request? [request]
  (= (get-in request [:headers "content-type"]) "application/json-patch+json"))

(defn json-patch
  [resource {:keys [body] :as request}]
  (if (json-patch-request? request)
    (->> (w/stringify-keys body)
         (json-safe-patch (w/stringify-keys resource))
         w/keywordize-keys
         (assoc request :body))
    request))


(defn edit-fn
  [resource-name & {:keys [pre-validate-hook
                           pre-delete-attrs-hook
                           immutable-keys
                           options]
                    :or   {pre-delete-attrs-hook pass-through
                           pre-validate-hook     pass-through
                           immutable-keys        []}}]
  (fn [{{uuid :uuid} :params :as request}]
    (try
      (let [resource (-> (str resource-name "/" uuid)
                         db/retrieve
                         (a/throw-cannot-edit request)
                         (sm/throw-can-not-do-action request))
            request  (json-patch resource request)]
        (-> resource
            (pre-delete-attrs-hook request)
            (u/delete-attributes request immutable-keys)
            u/update-timestamps
            (u/set-updated-by request)
            (pre-validate-hook request)
            (dissoc :operations)
            crud/validate
            (db/edit options)
            (update :body crud/set-operations request)))
      (catch Exception e
        (or (ex-data e) (throw e))))))

(defn throw-bulk-header-missing
  [{:keys [headers] :as _request}]
  (when-not (contains? headers "bulk")
    (throw (r/ex-bad-request "Bulk request should contain bulk http header."))))

(defn throw-bulk-require-cimi-filter
  [{{:keys [filter]} :body :as _request}]
  (when-not (coll? (impl/cimi-filter {:filter filter}))
    (throw (r/ex-bad-request "Bulk request should contain a non empty cimi filter."))))

(defn delete-fn
  [resource-name]
  (fn [{{uuid :uuid} :params :as request}]
    (try
      (-> (str resource-name "/" uuid)
          db/retrieve
          (a/throw-cannot-delete request)
          (sm/throw-can-not-do-action request)
          db/delete)
      (catch Exception e
        (or (ex-data e) (throw e))))))

(defn transform-entries
  [entries {{:keys [select]} :cimi-params :as request} with-entries-op?]
  (let [with-operations?  (and with-entries-op?
                               (or (nil? select) (contains? select "operations")))
        transformation-fn (remove nil? [(when select
                                          #(select-keys-resource % request))
                                        (when with-operations?
                                          #(crud/set-operations % request))])]
    (if (seq transformation-fn)
      (map (apply comp transformation-fn) entries)
      entries)))

(defn collection-wrapper-fn
  ([resource-name collection-acl collection-uri]
   (collection-wrapper-fn resource-name collection-acl collection-uri true true))
  ([resource-name collection-acl collection-uri with-collection-op? with-entries-op?]
   (fn [request entries]
     (let [resources (transform-entries entries request with-entries-op?)
           skeleton  {:acl           collection-acl
                      :resource-type collection-uri
                      :id            resource-name
                      :resources     resources}]
       (cond-> skeleton
               with-collection-op? (crud/set-operations request))))))


(defn query-fn
  [resource-name collection-acl collection-uri]
  (let [wrapper-fn (collection-wrapper-fn resource-name collection-acl collection-uri)]
    (validate-collection-acl collection-acl)
    (fn [request]
      (a/throw-cannot-query collection-acl request)
      (let [options           (select-keys request [:nuvla/authn :params :cimi-params])
            [metadata entries] (db/query resource-name options)
            updated-entries   (remove nil? (map #(a/select-viewable-keys % request) entries))
            entries-and-count (merge metadata (wrapper-fn request updated-entries))]
        (r/json-response entries-and-count)))))

(defn bulk-edit-fn
  ([resource-name collection-acl]
   (bulk-edit-fn resource-name collection-acl :set))
  ([resource-name collection-acl operation]
   (validate-collection-acl collection-acl)
   (fn [{:keys [body] :as request}]
     (throw-bulk-header-missing request)
     (a/throw-cannot-bulk-action collection-acl request)
     (let [cimi-params {:filter (impl/cimi-filter (select-keys body [:filter]))}
           options     (assoc (select-keys request [:nuvla/authn :body])
                         :cimi-params cimi-params
                         :operation operation)
           response    (db/bulk-edit resource-name options)]
       (r/json-response response)))))


(defn bulk-delete-fn
  [resource-name collection-acl _collection-uri]
  (validate-collection-acl collection-acl)
  (fn [{:keys [cimi-params] :as request}]
    (throw-bulk-header-missing request)
    (when-not (coll? (:filter cimi-params))
      (throw (r/ex-bad-request "Bulk request should contain a non empty cimi filter.")))
    (a/throw-cannot-bulk-delete collection-acl request)
    (let [options (select-keys request [:nuvla/authn :query-params :cimi-params])
          result  (db/bulk-delete resource-name options)]
      (r/json-response result))))

(defn bulk-action-fn
  [resource-name collection-acl _collection-uri]
  (validate-collection-acl collection-acl)
  (fn [{:keys [params body] :as request}]
    (throw-bulk-header-missing request)
    (throw-bulk-require-cimi-filter request)
    (a/throw-cannot-bulk-action collection-acl request)
    (let [active-claim (auth/current-active-claim request)
          acl          {:owners   ["group/nuvla-admin"]
                        :view-acl [active-claim]
                        :manage   [active-claim]}
          action-name  (-> params
                           :action
                           (str/replace #"-" "_")
                           (str "_" resource-name))]
      (job-utils/create-bulk-job action-name resource-name request acl body))))

(defn add-metric-fn
  [resource-name collection-acl _resource-uri & {:keys [validate-fn options]}]
  (validate-collection-acl collection-acl)
  (fn [{:keys [body] :as request}]
    (a/throw-cannot-add collection-acl request)
    (validate-fn body)
    (db/add-metric resource-name body options)))

(defn bulk-insert-metrics-fn
  [resource-name collection-acl _collection-uri]
  (validate-collection-acl collection-acl)
  (fn [{:keys [body] :as request}]
    (throw-bulk-header-missing request)
    (a/throw-cannot-add collection-acl request)
    (a/throw-cannot-bulk-action collection-acl request)
    (let [options  (select-keys request [:nuvla/authn :body])
          response (db/bulk-insert-metrics resource-name body options)]
      (r/json-response response))))

(defn generic-bulk-operation-fn
  [resource-name collection-acl bulk-op-fn]
  (validate-collection-acl collection-acl)
  (fn [request]
    (throw-bulk-header-missing request)
    (a/throw-cannot-bulk-action collection-acl request)
    (bulk-op-fn resource-name request)))

(def ^:const href-not-found-msg "requested href not found")


(def ^:const href-not-accessible-msg "requested href cannot be accessed")


(defn resolve-href-keep
  "Pulls in the resource identified by the value of the :href key and merges
   that resource with argument. Keys specified directly in the argument take
   precedence. Common attributes in the referenced resource are stripped. If
   :href doesn't exist or start with http(s):// the argument is returned
   unchanged.

   If a referenced document doesn't exist or if the user doesn't have read
   access to the document, then the method will throw."
  [{:keys [href] :as resource} authn-info]
  (if-not (or (str/blank? href)
              (str/starts-with? href "http://")
              (str/starts-with? href "https://"))
    (if-let [refdoc (crud/retrieve-by-id-as-admin href)]
      (try
        (a/throw-cannot-view-data refdoc {:nuvla/authn authn-info})
        (-> refdoc
            (u/strip-common-attrs)
            (u/strip-service-attrs)
            (dissoc :acl)
            (merge resource))
        (catch Exception _
          (throw (r/ex-bad-request (format "%s: %s" href-not-accessible-msg href)))))
      (throw (r/ex-bad-request (format "%s: %s" href-not-found-msg href))))
    resource))


(defn resolve-href
  "Like resolve-href-keep, except that the :href attributes are removed."
  [{:keys [href] :as resource} authn-info]
  (if href
    (-> resource
        (resolve-href-keep authn-info)
        (dissoc :href))
    resource))


(defn resolve-hrefs
  "Does a prewalk of the given argument, replacing any map with an :href
   attribute with the result of merging the referenced resource (see the
   resolve-href function)."
  [resource authn-info & [keep?]]
  (let [f (if keep? resolve-href-keep resolve-href)]
    (w/prewalk #(f % authn-info) resource)))


(defn initialize
  "Perform the initialization of the database for a given resource type. If an
   exception is thrown, it will be logged but then ignored."
  [resource-url spec]
  (try
    (db/initialize resource-url {:spec spec})
    (catch Exception e
      (log/errorf "exception when initializing database for %s: %s"
                  resource-url (.getMessage e)))))

(defn initialize-as-timeseries
  "Perform the initialization of the database for a given resource type stored as a time-series. If an
   exception is thrown, it will be logged but then ignored."
  ([resource-url spec]
   (initialize-as-timeseries resource-url spec {}))
  ([resource-url spec opts]
   (try
     (db/initialize resource-url (merge {:spec spec, :timeseries true} opts))
     (catch Exception e
       (log/errorf e "exception when initializing database for %s: %s"
                   resource-url (.getMessage e))))))


(defn add-if-absent
  [resource-id resource-url resource]
  (try
    (let [request {:params      {:resource-name resource-url}
                   :body        resource
                   :nuvla/authn auth/internal-identity}
          {:keys [status] :as response} (crud/add request)]
      (case status
        201 (log/infof "created %s resource" resource-id)
        409 (log/infof "%s resource already exists; new resource not created." resource-id)
        (log/errorf "unexpected status code (%s) when creating %s resource: %s"
                    (str status) resource-id response)))
    (catch Exception e
      (log/errorf "error when creating %s resource: %s\n%s"
                  resource-id
                  (str e)
                  (with-out-str (st/print-cause-trace e))))))
