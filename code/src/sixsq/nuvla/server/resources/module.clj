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
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.module-application :as module-application]
    [sixsq.nuvla.server.resources.module-applications-sets :as module-applications-sets]
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
    (utils/is-applications-sets? subtype) module-applications-sets/resource-type
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


(defn throw-price-error
  [{:keys [subtype price]}]
  (when price
    (config-nuvla/throw-stripe-not-configured)
    (when (utils/is-project? subtype)
      (throw (r/ex-response "Module of subtype project should not have a price attribute!" 400)))))


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
        (u/set-created-by request)
        (crud/add-acl request)
        crud/validate)
    {}))

(defn throw-cannot-access-registries-or-creds
  [{{{:keys [private-registries registries-credentials]} :content} :body :as request}]
  (when
    (and (seq private-registries)
         (< (-> {:params      {:resource-name infra-service/resource-type}
                 :cimi-params {:filter (parser/parse-cimi-filter
                                         (str "subtype='registry' and ("
                                              (->> private-registries
                                                   (map #(str "id='" % "'"))
                                                   (str/join " or "))
                                              ")"))
                               :last   0}
                 :nuvla/authn (:nuvla/authn request)}
                crud/query
                :body
                :count)
            (count private-registries)))
    (throw (r/ex-response "Private registries can't be resolved!" 403)))
  (when-let [creds (->> registries-credentials (remove str/blank?) seq)]
    (when (< (-> {:params      {:resource-name credential/resource-type}
                  :cimi-params {:filter (parser/parse-cimi-filter
                                          (str "subtype='infrastructure-service-registry' and ("
                                               (->> creds
                                                    (map #(str "id='" % "'"))
                                                    (str/join " or "))
                                               ")"))
                                :last   0}
                  :nuvla/authn (:nuvla/authn request)}
                 crud/query
                 :body
                 :count)
             (count creds))
      (throw (r/ex-response "Registries credentials can't be resolved!" 403)))))

(defn throw-compatibility-required-for-application
  [{:keys [subtype compatibility]}]
  (when (and (utils/is-application? subtype) (nil? compatibility))
    (throw (r/ex-response "Application subtype should have compatibility attribute set!" 400))))


(defmethod crud/add resource-type
  [{:keys [body] :as request}]

  (a/throw-cannot-add collection-acl request)
  (throw-colliding-path (:path body))
  (throw-cannot-access-registries-or-creds request)
  (throw-price-error body)
  (throw-compatibility-required-for-application body)

  (let [[{:keys [subtype] :as module-meta}
         {:keys [author commit] :as module-content}] (-> body u/strip-service-attrs
                                                         utils/split-resource)
        module-meta (dissoc module-meta :parent-path :published)]

    (if (utils/is-project? subtype)
      (db-add-module-meta module-meta request)
      (let [content-url     (subtype->resource-url subtype)

            content-body    (merge module-content {:resource-type content-url})

            content-request {:params      {:resource-name content-url}
                             :body        content-body
                             :nuvla/authn auth/internal-identity}

            response        (crud/add content-request)

            content-id      (-> response :body :resource-id)]
        (-> module-meta
            (assoc :versions [(cond-> {:href   content-id
                                       :author author}
                                      commit (assoc :commit commit))])
            (utils/set-price (auth/current-active-claim request))
            (db-add-module-meta request))))))


(defn retrieve-edn
  [{{uuid :uuid} :params :as request}]
  (-> (str resource-type "/" (-> uuid utils/split-uuid first))
      (db/retrieve request)
      (a/throw-cannot-view request)))


(defmethod crud/retrieve resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (let [{:keys [subtype] :as module-meta} (retrieve-edn request)
          is-not-project? (not (utils/is-project? subtype))]
      (-> module-meta
          (cond-> is-not-project? (utils/get-module-content uuid))
          utils/resolve-vendor-email
          (crud/set-operations request)
          (a/select-viewable-keys request)
          (r/json-response)))
    (catch IndexOutOfBoundsException _
      (r/response-not-found (str resource-type "/" uuid)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(def edit-impl (std-crud/edit-fn resource-type))


(defn edit-module
  [{{uuid-full :uuid} :params :as request} resource error-message]
  (let [uuid     (-> uuid-full utils/split-uuid first)
        response (-> request
                     (assoc :request-method :put
                            :params {:uuid          uuid
                                     :resource-type resource-type}
                            :body resource)
                     edit-impl)]
    (if (= (:status response) 200)
      response
      (throw (r/ex-response (str error-message ": " response) 500)))))


(defmethod crud/edit resource-type
  [{:keys [body] :as request}]
  (try
    (let [id          (str resource-type "/" (-> request :params :uuid))
          [module-meta
           {:keys [author commit] :as module-content}] (-> body
                                                           u/strip-service-attrs
                                                           utils/split-resource)
          {:keys [subtype versions price acl]} (crud/retrieve-by-id-as-admin id)
          module-meta (-> module-meta
                          (dissoc :parent-path :published)
                          (assoc :subtype subtype)
                          utils/set-parent-path)]

      (a/can-edit? {:acl acl} request)

      (if (utils/is-project? subtype)
        (->> module-meta
             (assoc request :body)
             edit-impl)
        (let [_              (throw-cannot-access-registries-or-creds request)
              content-url    (subtype->resource-url subtype)

              content-body   (some-> module-content (merge {:resource-type content-url}))

              content-id     (when content-body
                               (-> {:params      {:resource-name content-url}
                                    :body        content-body
                                    :nuvla/authn auth/internal-identity}
                                   crud/add
                                   :body
                                   :resource-id))

              versions       (when content-id
                               (conj versions
                                     (cond-> {:href   content-id
                                              :author author}
                                             commit (assoc :commit commit))))
              price-changed? (and config-nuvla/*stripe-api-key*
                                  (or (not= (:cent-amount-daily price)
                                            (get-in module-meta [:price :cent-amount-daily]))
                                      (not= (:currency price)
                                            (get-in module-meta [:price :currency]))))]
          (-> request
              (update-in [:cimi-params :select] disj "compatibility")
              (assoc :body
                     (cond-> module-meta
                             price-changed? (-> (assoc :price (merge price (:price module-meta)))
                                                (utils/set-price (auth/current-active-claim request)))
                             versions (assoc :versions versions)))
              edit-impl))))
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
  [{:keys [subtype versions] :as _module-meta} request]
  (doseq [version versions]
    (when version
      (delete-content (:href version) subtype)))
  (delete-impl request))


(defn delete-item
  [{:keys [subtype versions] :as module-meta} request version-index]
  (let [content-id       (utils/retrieve-content-id versions version-index)
        delete-response  (delete-content content-id subtype)
        updated-versions (remove-version versions version-index)
        module-meta      (-> module-meta
                             (assoc :versions updated-versions)
                             (utils/set-published))]
    (edit-module request module-meta "A failure happened during delete module item")

    delete-response))


(defmethod crud/delete resource-type
  [request]
  (try
    (-> (retrieve-edn request)
        (a/throw-cannot-edit request)
        (delete-all request))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type "delete-version"]
  [{{uuid-full :uuid} :params :as request}]
  (try
    (let [{:keys [versions] :as module-meta} (-> (retrieve-edn request)
                                                 (a/throw-cannot-edit request))
          [uuid version-index] (utils/split-uuid uuid-full)
          request (assoc-in request [:params :uuid] uuid)]
      (delete-item module-meta request (or version-index
                                           (utils/last-index versions))))
    (catch IndexOutOfBoundsException _
      (r/response-not-found (str resource-type "/" uuid-full)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


(defn create-validate-docker-compose-job
  [{:keys [id acl] :as _resource}]
  (try
    (let [{{job-id     :resource-id
            job-status :status} :body} (job/create-job id "validate-docker-compose"
                                                       acl
                                                       :priority 50)
          job-msg (str "validating application docker-compose " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response
                 "unable to create async job to validate application docker-compose" 500 id)))
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "validate-docker-compose"]
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)
        {:keys [subtype _acl] :as resource} (crud/retrieve-by-id-as-admin id)]
    (a/throw-cannot-manage resource request)
    (if (utils/is-application? subtype)
      (create-validate-docker-compose-job resource)
      (throw (r/ex-response "invalid subtype" 400)))))


(defn publish-version
  [{:keys [versions] :as resource} index publish]

  (if index
    (let [part-a   (subvec versions 0 index)
          part-b   (subvec versions (inc index))
          version  (-> versions
                       (nth index)
                       (assoc :published publish))
          versions (concat part-a [version] part-b)]
      (assoc resource :versions versions))
    resource))


(defn publish-unpublish
  [{{uuid-full :uuid} :params :as request} publish]
  (let [[uuid version-index] (utils/split-uuid uuid-full)
        id (str resource-type "/" uuid)
        {:keys [subtype versions] :as resource} (crud/retrieve-by-id-as-admin id)]
    (a/throw-cannot-manage resource request)
    (if (utils/is-project? subtype)
      (throw (r/ex-response "invalid subtype" 400))
      (edit-module
        request
        (-> resource
            (publish-version (or version-index
                                 (utils/last-index versions)) publish)
            utils/set-published)
        "Edit versions failed"))
    (r/map-response (str (if publish "published" "unpublished") " successfully") 200)))


(defmethod crud/do-action [resource-type "publish"]
  [request]
  (publish-unpublish request true))


(defmethod crud/do-action [resource-type "unpublish"]
  [request]
  (publish-unpublish request false))

(defmethod crud/do-action [resource-type "deploy"]
  [request]
  (-> request
      crud/retrieve
      r/throw-response-not-200
      :body
      (utils/throw-cannot-deploy request)
      (utils/generate-deployment-set-skeleton request)
      (utils/resolve-referenced-applications request)
      r/json-response))

(defmethod crud/set-operations resource-type
  [{:keys [id subtype] :as resource} {{uuid :uuid} :params :as request}]
  (let [id_with-version            (if uuid
                                     (str resource-type "/" uuid)
                                     id)
        validate-docker-compose-op (u/action-map id :validate-docker-compose)
        publish-op                 (u/action-map id_with-version :publish)
        unpublish-op               (u/action-map id_with-version :unpublish)
        delete-version-op          (u/action-map id_with-version :delete-version)
        deploy-op                  (u/action-map id_with-version :deploy)
        can-manage?                (a/can-manage? resource request)
        can-delete?                (a/can-delete? resource request)
        check-op-present?          (and can-manage? (utils/is-application? subtype))
        deploy-op-present?         (utils/can-deploy? resource request)
        publish-eligible?          (and can-manage? (not (utils/is-project? subtype)))]
    (cond-> (crud/set-standard-operations resource request)
            check-op-present? (update :operations conj validate-docker-compose-op)
            publish-eligible? (update :operations conj publish-op)
            publish-eligible? (update :operations conj unpublish-op)
            deploy-op-present? (update :operations conj deploy-op)
            can-delete? (update :operations conj delete-version-op))))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::module/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::module/schema)
  (md/register resource-metadata))
