(ns sixsq.nuvla.server.resources.module
  "
This resource represents a module--the generic term for any project, image,
component, or application.
"
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.module-application :as module-application]
    [sixsq.nuvla.server.resources.module-component :as module-component]
    [sixsq.nuvla.server.resources.module.utils :as module-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.module :as module]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [clojure.pprint :refer [pprint]]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::module/schema))
(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(defn subtype->resource-name
  [subtype]
  (case subtype
    "component" module-component/resource-type
    "application" module-application/resource-type
    "application_kubernetes" module-application/resource-type
    (throw (r/ex-bad-request (str "unknown module subtype: " subtype)))))


(defn subtype->resource-uri
  [subtype]
  (case subtype
    "component" module-component/resource-type
    "application" module-application/resource-type
    "application_kubernetes" module-application/resource-type
    (throw (r/ex-bad-request (str "unknown module subtype: " subtype)))))


(defn colliding-path?
  [path]
  (let [filter    (parser/parse-cimi-filter (format "path='%s'" path))
        query-map {:params         {:resource-name resource-type}
                   :request-method :put
                   :nuvla/authn    auth/internal-identity
                   :cimi-params    {:filter filter
                                    :last   0}}]
    (-> query-map
        crud/query
        :body
        :count
        pos?)))


(defn throw-colliding-path
  [path]
  (when (colliding-path? path)
    (throw (r/ex-response (str "path '" path "' already exist") 409))))


(defn create-compatibility-job
  [id subtype acl content]
  (when (and (= subtype "application") content)
    (try
      (let [{{job-id     :resource-id
              job-status :status} :body} (job/create-job id "application_compatibility_check"
                                           acl
                                           :priority 50)
            job-msg (str "checking application compatibility " id " with async " job-id)]
        (when (not= job-status 201)
          (throw (r/ex-response
                   "unable to create async job to check application compatibility" 500 id)))
        (event-utils/create-event id job-msg acl)
        (r/map-response job-msg 202 id job-id))
      (catch Exception e
        ; do nothing. If the job is not created it is not reason to panic
        (log/error (str "cannot check module compatibility: '" id e "'"))
        ))))


(defmethod crud/add resource-type
  [{:keys [body] :as request}]

  (a/throw-cannot-add collection-acl request)

  (throw-colliding-path (:path body))

  (let [[{:keys [subtype] :as module-meta}
         {:keys [author commit] :as module-content}] (-> body u/strip-service-attrs module-utils/split-resource)]

    (if (= "project" subtype)
      (let [module-meta (module-utils/set-parent-path module-meta)]

        (db/add                                             ; FIXME duplicated code
          resource-type
          (-> module-meta
              u/strip-service-attrs
              (crud/new-identifier resource-type)
              (assoc :resource-type resource-type)
              u/update-timestamps
              (crud/add-acl request)
              crud/validate)
          {}))
      (let [content-url     (subtype->resource-name subtype)
            content-uri     (subtype->resource-uri subtype)

            content-body    (merge module-content {:resource-type content-uri})

            content-request {:params      {:resource-name content-url}
                             :body        content-body
                             :nuvla/authn auth/internal-identity}

            response        (crud/add content-request)

            content-id      (-> response :body :resource-id)
            module-meta     (module-utils/set-parent-path
                              (assoc module-meta :versions [(cond-> {:href   content-id
                                                                     :author author}
                                                                    commit (assoc :commit commit))]))

            user-id         (auth/current-user-id request)
            job-acl             {:owners   ["group/nuvla-admin"]
                                 :view-acl [user-id]}

            module          (db/add
                              resource-type
                              (-> module-meta
                                u/strip-service-attrs
                                (crud/new-identifier resource-type)
                                (assoc :resource-type resource-type)
                                u/update-timestamps
                                (crud/add-acl request)
                                crud/validate)
                              {})

            content-compose (:docker-compose (:content body))]

        (create-compatibility-job (:resource-id (:body module)) subtype job-acl content-compose)
        module))))


(defn split-uuid
  [uuid]
  (let [[uuid-module index] (str/split uuid #"_")
        index (some-> index read-string)]
    [uuid-module index]))


(defn retrieve-edn
  [{{uuid :uuid} :params :as request}]
  (-> (str resource-type "/" (-> uuid split-uuid first))
      (db/retrieve request)
      (a/throw-cannot-view request)))


(defn retrieve-content-id
  [versions index]
  (if index
    (-> versions (nth index) :href)
    (->> versions (remove nil?) last :href)))


(defmethod crud/retrieve resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (let [{:keys [versions] :as module-meta} (retrieve-edn request)
          version-index  (second (split-uuid uuid))
          version-id     (retrieve-content-id versions version-index)
          module-content (if version-id
                           (-> version-id
                               (crud/retrieve-by-id-as-admin)
                               (dissoc :resource-type :operations :acl))
                           (when version-index
                             (throw (r/ex-not-found
                                      (str "Module version not found: " resource-type "/" uuid)))))]
      (-> (assoc module-meta :content module-content)
          (crud/set-operations request)
          (a/select-viewable-keys request)
          (r/json-response)))
    (catch IndexOutOfBoundsException _
      (r/response-not-found (str resource-type "/" uuid)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{:keys [body] :as request}]
  (try
    (let [id (str resource-type "/" (-> request :params :uuid))
          [module-meta {:keys [author commit] :as module-content}]
          (-> body u/strip-service-attrs module-utils/split-resource)
          {:keys [subtype versions acl]} (crud/retrieve-by-id-as-admin id)]

      (a/can-edit? {:acl acl} request)

      (if (= "project" subtype)
        (let [module-meta (module-utils/set-parent-path (assoc module-meta :subtype subtype))]

          (edit-impl (assoc request :body module-meta)))
        (let [content-url     (subtype->resource-name subtype)
              content-uri     (subtype->resource-uri subtype)

              content-body    (merge module-content {:resource-type content-uri})

              content-request {:params      {:resource-name content-url}
                               :body        content-body
                               :nuvla/authn auth/internal-identity}

              response        (crud/add content-request)

              content-id      (-> response :body :resource-id)

              versions        (conj versions (cond-> {:href   content-id
                                                      :author author}
                                                     commit (assoc :commit commit)))
              module-meta     (module-utils/set-parent-path
                                (assoc module-meta :versions versions
                                                   :subtype subtype))

              module          (edit-impl (assoc request :body module-meta))

              content-compose (:docker-compose (:content body))
              id              (:id (:body module))]

          (when id
            (create-compatibility-job (:id (:body module)) subtype (:acl (:body module)) content-compose))
          module
          )))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn remove-version
  [versions index]
  (let [part-a (subvec versions 0 index)
        part-b (subvec versions (inc index))]
    (concat part-a [nil] part-b)))


(def delete-impl (std-crud/delete-fn resource-type))


(defn delete-content
  [content-id subtype]
  (let [delete-request {:params      {:uuid          (-> content-id u/parse-id second)
                                      :resource-name (subtype->resource-name subtype)}
                        :body        {:id content-id}
                        :nuvla/authn auth/internal-identity}]
    (crud/delete delete-request)))


(defn delete-all
  [request {:keys [subtype versions] :as module-meta}]
  (doseq [version versions]
    (when version
      (delete-content (:href version) subtype)))
  (delete-impl request))


(defn delete-item
  [request {:keys [subtype versions] :as module-meta} version-index]
  (let [content-id       (retrieve-content-id versions version-index)
        delete-response  (delete-content content-id subtype)
        updated-versions (remove-version versions version-index)
        module-meta      (assoc module-meta :versions updated-versions)
        {:keys [status]} (edit-impl (assoc request :request-method :put
                                                   :body module-meta))]
    (when (not= status 200)
      (throw (r/ex-response "A failure happened during delete module item" 500)))

    delete-response))


(defmethod crud/delete resource-type
  [{{uuid-full :uuid} :params :as request}]
  (try

    (let [module-meta (retrieve-edn request)

          _           (a/throw-cannot-edit module-meta request)

          [uuid version-index] (split-uuid uuid-full)
          request     (assoc-in request [:params :uuid] uuid)]

      (if version-index
        (delete-item request module-meta version-index)
        (delete-all request module-meta)))

    (catch IndexOutOfBoundsException _
      (r/response-not-found (str resource-type "/" uuid-full)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::module/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::module/schema)
  (md/register resource-metadata))
