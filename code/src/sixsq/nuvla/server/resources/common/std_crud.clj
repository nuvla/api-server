(ns sixsq.nuvla.server.resources.common.std-crud
  "Standard CRUD functions for resources."
  (:require
    [clojure.data.json :as json]
    [clojure.stacktrace :as st]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [clojure.walk :as w]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.acl-collection :as acl-collection]
    [sixsq.nuvla.server.util.response :as r]))


(def validate-collection-acl (u/create-spec-validation-fn ::acl-collection/acl))


(defn add-fn
  [resource-name collection-acl resource-uri]
  (validate-collection-acl collection-acl)
  (fn [{:keys [body] :as request}]
    (a/throw-cannot-add collection-acl request)
    (db/add
      resource-name
      (-> body
          u/strip-service-attrs
          (crud/new-identifier resource-name)
          (assoc :resource-type resource-uri)
          u/update-timestamps
          (u/set-created-by request)
          (crud/add-acl request)
          crud/validate)
      {})))


(defn retrieve-fn
  [resource-name]
  (fn [{{uuid :uuid} :params :as request}]
    (try
      (-> (str resource-name "/" uuid)
          (db/retrieve request)
          (a/throw-cannot-view request)
          (crud/set-operations request)
          (a/select-viewable-keys request)
          r/json-response)
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defn edit-fn
  [resource-name]
  (fn [{{select :select} :cimi-params {uuid :uuid} :params body :body :as request}]
    (try
      (let [{:keys [acl] :as current} (->
                                        (str resource-name "/" uuid)
                                        (db/retrieve (assoc-in request [:cimi-params :select] nil))
                                        (a/throw-cannot-edit request))
            rights                   (a/extract-rights (auth/current-authentication request) acl)
            dissoc-keys              (-> (map keyword select)
                                         set
                                         u/strip-select-from-mandatory-attrs
                                         (a/editable-keys rights))
            current-without-selected (apply dissoc current dissoc-keys)
            editable-body            (select-keys body (-> body keys (a/editable-keys rights)))
            merged                   (merge current-without-selected editable-body)]
        (-> merged
            u/update-timestamps
            (u/set-updated-by request)
            crud/validate
            (db/edit request)))
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defn delete-fn
  [resource-name]
  (fn [{{uuid :uuid} :params :as request}]
    (try
      (-> (str resource-name "/" uuid)
          (db/retrieve request)
          (a/throw-cannot-delete request)
          (db/delete request))
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defn collection-wrapper-fn
  ([resource-name collection-acl collection-uri]
   (collection-wrapper-fn resource-name collection-acl collection-uri true true))
  ([resource-name collection-acl collection-uri with-collection-op? with-entries-op?]
   (fn [request entries]
     (let [resources (cond->> entries
                              with-entries-op? (map #(crud/set-operations % request)))
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
      (let [options           (select-keys request [:nuvla/authn :query-params :cimi-params])
            [metadata entries] (db/query resource-name options)
            updated-entries   (remove nil? (map #(a/select-viewable-keys % request) entries))
            entries-and-count (merge metadata (wrapper-fn request updated-entries))]
        (r/json-response entries-and-count)))))


(defn throw-bulk-header-missing
  [{:keys [headers] :as _request}]
  (when-not (contains? headers "bulk")
    (throw (r/ex-bad-request "Bulk request should contain bulk http header."))))


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
    (when-not (coll? (impl/cimi-filter {:filter (:filter body)}))
      (throw (r/ex-bad-request "Bulk request should contain a non empty cimi filter.")))
    (a/throw-cannot-bulk-action collection-acl request)
    (let [authn-info     (auth/current-authentication request)
          active-claim   (auth/current-active-claim request)
          action         (:action params)
          action-name    (-> action
                             (str/replace #"-" "_")
                             (str "_" resource-name))
          create-request {:params      {:resource-name "job"}
                          :body        {:action          action-name
                                        :target-resource {:href resource-name}
                                        :payload         (-> body
                                                             (assoc :authn-info authn-info)
                                                             (json/write-str))
                                        :acl             {:owners   ["group/nuvla-admin"]
                                                          :view-acl [active-claim]}}
                          :nuvla/authn auth/internal-identity}
          {{job-id     :resource-id
            job-status :status} :body} (crud/add create-request)]
      (when (not= job-status 201)
        (throw (r/ex-response
                 (format "unable to create async job for %s " action)
                 500 resource-name)))
      (r/map-response (str "starting " action " with async " job-id)
                      202 resource-name job-id))))


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
