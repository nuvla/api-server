(ns sixsq.nuvla.server.resources.module
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.module-application :as module-application]
    [sixsq.nuvla.server.resources.module-component :as module-component]
    [sixsq.nuvla.server.resources.module-image :as module-image]
    [sixsq.nuvla.server.resources.module.utils :as module-utils]
    [sixsq.nuvla.server.resources.spec.module :as module]
    [sixsq.nuvla.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::module/module))
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

(defn type->resource-name
  [type]
  (case type
    "IMAGE" module-image/resource-type
    "COMPONENT" module-component/resource-type
    "APPLICATION" module-application/resource-type
    (throw (r/ex-bad-request (str "unknown module type: " type)))))


(defn type->resource-uri
  [type]
  (case type
    "IMAGE" module-image/resource-type
    "COMPONENT" module-component/resource-type
    "APPLICATION" module-application/resource-type
    (throw (r/ex-bad-request (str "unknown module type: " type)))))


(defmethod crud/add resource-type
  [{:keys [body] :as request}]
  (a/can-modify? {:acl collection-acl} request)
  (let [[{:keys [type] :as module-meta}
         {:keys [author commit] :as module-content}] (-> body u/strip-service-attrs module-utils/split-resource)]

    (if (= "PROJECT" type)
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
      (let [content-url (type->resource-name type)
            content-uri (type->resource-uri type)

            content-body (merge module-content {:resource-type content-uri})

            content-request {:params   {:resource-name content-url}
                             :identity std-crud/internal-identity
                             :body     content-body}

            response (crud/add content-request)

            content-id (-> response :body :resource-id)
            module-meta (-> (assoc module-meta :versions [(cond-> {:href   content-id
                                                                   :author author}
                                                                  commit (assoc :commit commit))])
                            module-utils/set-parent-path)]

        (db/add
          resource-type
          (-> module-meta
              u/strip-service-attrs
              (crud/new-identifier resource-type)
              (assoc :resource-type resource-type)
              u/update-timestamps
              (crud/add-acl request)
              crud/validate)
          {})))))

(defn split-uuid
  [uuid]
  (let [[uuid-module index] (str/split uuid #"_")
        index (some-> index read-string)]
    [uuid-module index]))

(defn retrieve-edn
  [{{uuid :uuid} :params :as request}]
  (-> (str resource-type "/" (-> uuid split-uuid first))
      (db/retrieve request)
      (a/can-view? request)))


(defn retrieve-content-id
  [versions index]
  (if index
    (-> versions (nth index) :href)
    (->> versions (remove nil?) last :href)))


(defmethod crud/retrieve resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (let [{:keys [versions] :as module-meta} (retrieve-edn request)
          version-index (second (split-uuid uuid))
          version-id (retrieve-content-id versions version-index)
          module-content (if version-id
                           (-> version-id
                               (crud/retrieve-by-id-as-admin)
                               (dissoc :resource-type :operations :acl))
                           (when version-index
                             (throw (r/ex-not-found (str "Module version not found: " resource-type "/" uuid)))))]
      (-> (assoc module-meta :content module-content)
          (crud/set-operations request)
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
          {:keys [type versions acl]} (crud/retrieve-by-id-as-admin id)

          _ (a/can-modify? {:acl acl} request)

          content-url (type->resource-name type)
          content-uri (type->resource-uri type)

          content-body (merge module-content {:resource-type content-uri})

          content-request {:params   {:resource-name content-url}
                           :identity std-crud/internal-identity
                           :body     content-body}

          response (crud/add content-request)

          content-id (-> response :body :resource-id)

          versions (conj versions (cond-> {:href   content-id
                                           :author author}
                                          commit (assoc :commit commit)))
          module-meta (-> (assoc module-meta :versions versions
                                             :type type)
                          module-utils/set-parent-path)]

      (edit-impl (assoc request :body module-meta)))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defn remove-version
  [versions index]
  (let [part-a (subvec versions 0 index)
        part-b (subvec versions (inc index))]
    (concat part-a [nil] part-b)))


(def delete-impl (std-crud/delete-fn resource-type))

(defn delete-content
  [content-id type]
  (let [delete-request {:params   {:uuid          (-> content-id u/split-resource-id second)
                                   :resource-name (type->resource-name type)}
                        :identity std-crud/internal-identity
                        :body     {:id content-id}}]
    (crud/delete delete-request)))

(defn delete-all
  [request {:keys [type versions] :as module-meta}]
  (doseq [version versions]
    (when version
      (delete-content (:href version) type)))
  (delete-impl request))

(defn delete-item
  [request {:keys [type versions] :as module-meta} version-index]
  (let [content-id (retrieve-content-id versions version-index)
        delete-response (delete-content content-id type)
        updated-versions (remove-version versions version-index)
        module-meta (assoc module-meta :versions updated-versions)
        {:keys [status]} (edit-impl (assoc request :request-method :put
                                                   :body module-meta))]
    (when (not= status 200)
      (throw (r/ex-response "A failure happened during delete module item" 500)))

    delete-response))

(defmethod crud/delete resource-type
  [{{uuid-full :uuid} :params :as request}]
  (try

    (let [module-meta (retrieve-edn request)

          _ (a/can-modify? module-meta request)

          [uuid version-index] (split-uuid uuid-full)
          request (assoc-in request [:params :uuid] uuid)]

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
(defn initialize
  []
  (std-crud/initialize resource-type ::module/module))
