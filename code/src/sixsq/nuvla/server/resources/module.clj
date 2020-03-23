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
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.module-application :as module-application]
    [sixsq.nuvla.server.resources.module-component :as module-component]
    [sixsq.nuvla.server.resources.module.utils :as utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.module :as module]
    [sixsq.nuvla.server.util.metadata :as gen-md]
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


(defn subtype->resource-url
  [subtype]
  (cond
    (utils/is-component? subtype) module-component/resource-type
    (utils/is-application? subtype) module-application/resource-type
    (utils/is-application-k8s? subtype) module-application/resource-type
    :else (throw (r/ex-bad-request (str "unknown module subtype: " subtype)))))


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


(defn db-add-module-meta
  [module-meta request]
  (db/add
    resource-type
    (-> module-meta
        utils/set-parent-path
        u/strip-service-attrs
        (crud/new-identifier resource-type)
        (assoc :resource-type resource-type)
        u/update-timestamps
        (crud/add-acl request)
        crud/validate)
    {}))


(defmethod crud/add resource-type
  [{:keys [body] :as request}]

  (a/throw-cannot-add collection-acl request)

  (throw-colliding-path (:path body))

  (let [[{:keys [subtype] :as module-meta}
         {:keys [author commit docker-compose] :as module-content}] (-> body u/strip-service-attrs
                                                                        utils/split-resource)
        module-meta (dissoc module-meta :compatibility)]

    (if (utils/is-project? subtype)
      (db-add-module-meta module-meta request)
      (let [content-url     (subtype->resource-url subtype)

            [compatibility
             unsupported-options] (when (utils/is-application? subtype)
                                    (-> docker-compose
                                        utils/parse-and-throw-when-not-parsable-docker-compose
                                        utils/get-compatibility-fields))

            content-body    (-> module-content
                                (dissoc :unsupported-options)
                                (merge {:resource-type content-url})
                                (cond-> (seq unsupported-options) (assoc :unsupported-options
                                                                         unsupported-options)))

            content-request {:params      {:resource-name content-url}
                             :body        content-body
                             :nuvla/authn auth/internal-identity}

            response        (crud/add content-request)

            content-id      (-> response :body :resource-id)]
        (-> module-meta
            (assoc :versions [(cond-> {:href   content-id
                                       :author author}
                                      commit (assoc :commit commit))])
            (cond-> compatibility (assoc :compatibility compatibility))
            (db-add-module-meta request))))))


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
    (let [id          (str resource-type "/" (-> request :params :uuid))
          [module-meta
           {:keys [author commit docker-compose] :as module-content}] (-> body
                                                                          u/strip-service-attrs
                                                                          utils/split-resource)
          {:keys [subtype versions acl]} (crud/retrieve-by-id-as-admin id)
          module-meta (-> module-meta
                          (dissoc :compatibility)
                          (assoc :subtype subtype)
                          utils/set-parent-path)]

      (a/can-edit? {:acl acl} request)

      (if (utils/is-project? subtype)
        (->> module-meta
             (assoc request :body)
             edit-impl)
        (let [content-url     (subtype->resource-url subtype)

              [compatibility
               unsupported-options] (when (utils/is-application? subtype)
                                      (-> docker-compose
                                          utils/parse-and-throw-when-not-parsable-docker-compose
                                          utils/get-compatibility-fields))

              content-body    (-> module-content
                                  (dissoc :unsupported-options)
                                  (merge {:resource-type content-url})
                                  (cond-> (seq unsupported-options) (assoc :unsupported-options
                                                                           unsupported-options)))

              content-request {:params      {:resource-name content-url}
                               :body        content-body
                               :nuvla/authn auth/internal-identity}

              response        (crud/add content-request)

              content-id      (-> response :body :resource-id)

              versions        (conj versions (cond-> {:href   content-id
                                                      :author author}
                                                     commit (assoc :commit commit)))]

          (edit-impl
            (assoc request
              :body
              (-> module-meta
                  (assoc :versions versions)
                  (cond-> compatibility (assoc :compatibility compatibility))))))))
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
                                      :resource-name (subtype->resource-url subtype)}
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
    (let [module-meta (-> (retrieve-edn request)
                          (a/throw-cannot-edit request))

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


(defn create-check-docker-compose-job
  [{:keys [id acl] :as resource}]
  (try
    (let [{{job-id     :resource-id
            job-status :status} :body} (job/create-job id "check-docker-compose"
                                                       acl
                                                       :priority 50)
          job-msg (str "checking application docker-compose " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response
                 "unable to create async job to check application docker-compose" 500 id)))
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "check-docker-compose"]
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)
        {:keys [subtype acl] :as resource} (crud/retrieve-by-id-as-admin id)]
    (a/throw-cannot-manage resource request)
    (if (utils/is-application? subtype)
      (create-check-docker-compose-job resource)
      (throw (r/ex-response "invalid subtype" 400)))))


(defmethod crud/set-operations resource-type
  [{:keys [id subtype] :as resource} request]
  (let [check-docker-compose-op (u/action-map id :check-docker-compose)
        check-op-present?       (and (a/can-manage? resource request)
                                     (utils/is-application? subtype))]
    (cond-> (crud/set-standard-operations resource request)
            check-op-present? (update :operations conj check-docker-compose-op))))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::module/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::module/schema)
  (md/register resource-metadata))
