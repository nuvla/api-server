(ns sixsq.nuvla.server.resources.module
  "
This resource represents a module--the generic term for any project, image,
component, or application.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
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
  [resource]
  (cond
    (utils/is-component? resource) module-component/resource-type
    (utils/is-application? resource) module-application/resource-type
    (utils/is-application-k8s? resource) module-application/resource-type
    (utils/is-applications-sets? resource) module-applications-sets/resource-type
    :else (throw (r/ex-bad-request (str "unknown module subtype: "
                                        (utils/module-subtype resource))))))


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
  [{{:keys [path]} :body :as request}]
  (if (colliding-path? path)
    (throw (r/ex-response (str "path '" path "' already exist") 409))
    request))


(defn throw-price-error
  [{{:keys [price] :as body} :body :as request}]
  (if price
    (do
      (config-nuvla/throw-stripe-not-configured)
      (when (utils/is-project? body)
        (throw (r/ex-response "Module of subtype project should not have a price attribute!" 400))))
    request))


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
  [{{:keys [compatibility] :as resource} :body :as request}]
  (if (and (utils/is-application? resource)
           (nil? compatibility))
    (throw (r/ex-response "Application subtype should have compatibility attribute set!" 400))
    request))


(defmethod crud/add resource-type
  [{:keys [body] :as request}]
  (->> request
       (a/throw-cannot-add collection-acl)
       throw-colliding-path
       throw-cannot-access-registries-or-creds
       throw-price-error
       throw-compatibility-required-for-application)
  (let [[module-meta
         {:keys [author commit]
          :as   module-content}] (utils/split-resource body)
        module-meta (dissoc module-meta :parent-path :published)]

    (if (utils/is-project? module-meta)
      (db-add-module-meta module-meta request)
      (let [content-url     (subtype->resource-url module-meta)

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

(defmethod crud/retrieve resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (-> request
        utils/retrieve-module-meta
        (utils/retrieve-module-content request)
        utils/resolve-vendor-email
        (crud/set-operations request)
        (a/select-viewable-keys request)
        (r/json-response))
    (catch IndexOutOfBoundsException _
      (r/response-not-found (str resource-type "/" uuid)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(def edit-impl (std-crud/edit-fn resource-type))

(defn edit-module
  [resource {{full-uuid :uuid} :params :as request} error-message]
  (let [response (-> request
                     (assoc :request-method :put
                            :params {:uuid          (utils/full-uuid->uuid full-uuid)
                                     :resource-type resource-type}
                            :body resource)
                     edit-impl)]
    (if (= (:status response) 200)
      response
      (throw (r/ex-response (str error-message ": " response) 500)))))


(defmethod crud/edit resource-type
  [{:keys [body] :as request}]
  (try
    (let [{:keys [subtype versions price]} (-> request
                                               utils/retrieve-module-meta
                                               (a/throw-cannot-edit request))
          [module-meta
           {:keys [author commit]
            :as   module-content}] (utils/split-resource body)
          module-meta (-> module-meta
                          (dissoc :parent-path :published)
                          (assoc :subtype subtype)
                          utils/set-parent-path)]

      (if (utils/is-project? module-meta)
        (->> module-meta
             (assoc request :body)
             edit-impl)
        (let [_              (throw-cannot-access-registries-or-creds request)
              content-url    (subtype->resource-url module-meta)

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
  [{:keys [versions] :as _module-meta} version-index]
  (let [part-a (subvec versions 0 version-index)
        part-b (subvec versions (inc version-index))]
    (concat part-a [nil] part-b)))


(def delete-impl (std-crud/delete-fn resource-type))


(defn delete-content
  [content-id]
  (log/error "(u/id->request-params content-id)" (u/id->request-params content-id))
  (let [delete-request {:params      (u/id->request-params content-id)
                        :body        {:id content-id}
                        :nuvla/authn auth/internal-identity}]
    (crud/delete delete-request)))


(defn delete-all
  [{:keys [versions] :as _module-meta} request]
  (doseq [version versions]
    (when version
      (delete-content (:href version))))
  (delete-impl request))


(defn delete-item
  [module-meta {{full-uuid :uuid} :params :as request}]
  (let [version-index    (utils/latest-or-version-index module-meta full-uuid)
        _ (log/error "version-index" version-index)
        content-id       (utils/get-content-id module-meta version-index)
        _ (log/error "content-id" content-id)
        delete-response  (delete-content content-id)
        _ (log/error "delete-response" delete-response)
        updated-versions (remove-version module-meta version-index)
        module-meta      (-> module-meta
                             (assoc :versions updated-versions)
                             (utils/set-published))]
    (log/error "deleted version module-meta" module-meta)
    (log/error "edit request" (update-in request [:params :uuid] utils/full-uuid->uuid)
               (edit-module module-meta
                            (update-in request [:params :uuid] utils/full-uuid->uuid)
                            "A failure happened during delete module item"))
    (edit-module module-meta
                 (update-in request [:params :uuid] utils/full-uuid->uuid)
                 "A failure happened during delete module item")

    delete-response))


(defmethod crud/delete resource-type
  [request]
  (try
    (-> request
        utils/retrieve-module-meta
        (a/throw-cannot-edit request)
        (delete-all request))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type "delete-version"]
  [{{full-uuid :uuid} :params :as request}]
  (try
    (let [module-meta (-> request
                          utils/retrieve-module-meta
                          (a/throw-cannot-edit request))]
      (delete-item module-meta request))
    (catch IndexOutOfBoundsException _
      (r/response-not-found (str resource-type "/" full-uuid)))
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
        {:keys [_acl] :as resource} (crud/retrieve-by-id-as-admin id)]
    (a/throw-cannot-manage resource request)
    (if (utils/is-application? resource)
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
  [{{full-uuid :uuid} :params :as request} publish]
  (let [module-meta (utils/retrieve-module-meta request)]
    (-> (a/throw-cannot-manage module-meta request)
        utils/throw-cannot-publish-project
        (publish-version
          (utils/latest-or-version-index module-meta full-uuid)
          publish)
        utils/set-published
        (edit-module request "Edit versions failed"))
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
      crud/get-resource-throw-nok
      (utils/throw-cannot-deploy request)
      (utils/generate-deployment-set-skeleton request)
      (utils/resolve-referenced-applications request)
      r/json-response))

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} {{uuid :uuid} :params :as request}]
  (let [id-with-version            (if uuid
                                     (str resource-type "/" uuid)
                                     id)
        validate-docker-compose-op (u/action-map id :validate-docker-compose)
        publish-op                 (u/action-map id-with-version :publish)
        unpublish-op               (u/action-map id-with-version :unpublish)
        delete-version-op          (u/action-map id-with-version :delete-version)
        deploy-op                  (u/action-map id-with-version :deploy)
        can-manage?                (a/can-manage? resource request)
        can-delete?                (a/can-delete? resource request)
        check-op-present?          (and can-manage? (utils/is-application? resource))
        deploy-op-present?         (utils/can-deploy? resource request)
        publish-eligible?          (and can-manage? (utils/is-not-project? resource))]
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
