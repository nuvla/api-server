(ns com.sixsq.nuvla.server.resources.module
  "
This resource represents a module--the generic term for any project, image,
component, or application.
"
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.event-config :as ec]
    [com.sixsq.nuvla.server.resources.common.event-context :as ectx]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.module-application :as module-application]
    [com.sixsq.nuvla.server.resources.module-application-helm :as module-application-helm]
    [com.sixsq.nuvla.server.resources.module-applications-sets :as module-applications-sets]
    [com.sixsq.nuvla.server.resources.module-component :as module-component]
    [com.sixsq.nuvla.server.resources.module.utils :as utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.module :as module]
    [com.sixsq.nuvla.server.util.general :as gen-util]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]))


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

(defn subtype->collection-uri
  [resource]
  (cond
    (utils/is-component? resource) module-component/resource-type
    (utils/is-application? resource) module-application/resource-type
    (utils/is-application-helm? resource) module-application-helm/resource-type
    (utils/is-application-k8s? resource) module-application/resource-type
    (utils/is-applications-sets? resource) module-applications-sets/resource-type
    :else (throw (r/ex-bad-request (str "unknown module subtype: "
                                        (utils/module-subtype resource))))))

(defn colliding-path?
  [path]
  (-> resource-type
      (crud/query-count (format "path='%s'" path) {:nuvla/authn auth/internal-identity})
      pos?))

(defn throw-colliding-path
  [{{:keys [path]} :body :as request}]
  (if (colliding-path? path)
    (throw (r/ex-response (str "path '" path "' already exist") 409))
    request))

(defn throw-project-cannot-have-price
  [{{:keys [price] :as resource} :body :as request}]
  (if price
    (do
      (config-nuvla/throw-stripe-not-configured)
      (if (utils/is-project? resource)
        (throw (r/ex-response "Project should not have a price attribute!" 400))
        request))
    request))

(defn throw-project-cannot-have-content
  [{{:keys [content] :as resource} :body :as request}]
  (if (and (utils/is-project? resource) content)
    (throw (r/ex-response "Project should not have content attribute!" 400))
    request))

(defn throw-application-requires-compatibility
  [{{:keys [compatibility] :as resource} :body :as request}]
  (if (and (utils/is-application? resource)
           (nil? compatibility))
    (throw (r/ex-response "Application subtype should have compatibility attribute set!" 400))
    request))

(defn throw-requires-parent
  [{{:keys [path] :as resource} :body :as request}]
  (if (and (utils/is-not-project? resource)
           (str/blank? (utils/get-parent-path path)))
    (throw (r/ex-bad-request "Application subtype must have a parent project!"))
    request))

(defn query-by-path [path request]
  (-> (db/query
        resource-type
        {:cimi-params {:filter (parser/parse-cimi-filter
                                 (str "path='"
                                      path
                                      "'"))}
         :nuvla/authn (auth/current-authentication request)})
      second
      first))


(defn- throw-if-not-exists-or-found [resource parent-path]
  (if (nil? resource)
    (throw (r/ex-bad-request (str "No parent project found for path: " parent-path)))
    resource))

(defn- throw-if-no-edit-rights [resource request]
  (try
    (a/throw-cannot-edit resource request)
    (catch Exception _
      (throw (r/ex-response (str "You do not have edit rights for: "
                                 (:id resource)
                                 " at path: "
                                 (:path resource))
                            403)))))

(defn throw-requires-editable-parent-project
  [{{:keys [path]} :body :as request}]
  (let [parent-path (utils/get-parent-path path)]
    (if (and (not (str/blank? parent-path))
             (-> (query-by-path parent-path request)
                 (throw-if-not-exists-or-found parent-path)
                 (throw-if-no-edit-rights request)
                 utils/is-not-project?))
      (throw (r/ex-response "Parent must be a project!" 403))
      request)))


(defn throw-project-cannot-delete-if-has-children
  [{:keys [path] :as resource}]
  (if (and (utils/is-project? resource)
           (pos? (utils/count-children-as-admin path)))
    (throw (r/ex-response "Cannot delete project with children" 403))
    resource))


(defn remove-version
  [{:keys [versions] :as _module-meta} version-index]
  (let [part-a (subvec versions 0 version-index)
        part-b (subvec versions (inc version-index))]
    (concat part-a [nil] part-b)))

(defn add-version
  [{{:keys [author commit]} :content :as module} content-href]
  (update module :versions conj (cond-> {:href   content-href
                                         :author author}
                                        commit (assoc :commit commit))))

(defn create-content
  [module]
  (if-let [content (:content module)]
    (let [collection-uri (subtype->collection-uri module)
          content-body   (merge content {:resource-type collection-uri})
          content-href   (-> (crud/add {:params      {:resource-name collection-uri}
                                        :body        content-body
                                        :nuvla/authn auth/internal-identity})
                             :body
                             :resource-id)]
      (add-version module content-href))
    module))

(defn throw-cannot-access-registries-or-creds
  [request]
  (-> request
      utils/throw-cannot-access-private-registries-for-request
      utils/throw-cannot-access-registries-credentials-for-request
      utils/throw-can-not-access-helm-repo-url-for-request
      utils/throw-can-not-access-helm-repo-cred-for-request))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defn update-add-request
  [request]
  (update request :body
          #(-> %
               (dissoc :parent-path :published :versions)
               utils/set-parent-path
               create-content
               (dissoc :content)
               (utils/set-price nil request))))

(def event-context-keys [:name
                         :description
                         :path
                         :subtype])

(defn set-event-context
  [resource]
  (ectx/add-to-context :resource (select-keys resource event-context-keys))
  resource)

(defmethod crud/add resource-type
  [request]
  (a/throw-cannot-add collection-acl request)
  (-> request
      throw-colliding-path
      throw-cannot-access-registries-or-creds
      throw-project-cannot-have-price
      throw-project-cannot-have-content
      throw-application-requires-compatibility
      throw-requires-parent
      throw-requires-editable-parent-project
      update-add-request
      set-event-context
      add-impl))

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
    (if (r/status-200? response)
      (do
        (set-event-context resource)
        response)
      (throw (r/ex-response (str error-message ": " response) 500)))))


(defn update-edit-request
  [request]
  (let [existing-module (-> request
                            utils/retrieve-module-meta
                            (a/throw-cannot-edit request))]
    (-> request
        (update-in [:cimi-params :select] disj "compatibility")
        (update :body
                #(-> %
                     (gen-util/merge-and-ignore-input-immutable-attrs
                       existing-module [:parent-path :published :versions :subtype])
                     utils/set-parent-path
                     create-content
                     (dissoc :content)
                     (utils/set-price existing-module request))))))

(defmethod crud/edit resource-type
  [request]
  (try
    (-> request
        throw-cannot-access-registries-or-creds
        throw-project-cannot-have-price
        throw-project-cannot-have-content
        update-edit-request
        edit-impl)
    (catch Exception e
      (or (ex-data e) (throw e)))))

(def delete-impl (std-crud/delete-fn resource-type))

(defn delete-content
  [module-meta content-id]
  (crud/delete
    {:params      {:resource-name (subtype->collection-uri module-meta)
                   :uuid          (u/id->uuid content-id)}
     :nuvla/authn auth/internal-identity}))


(defn delete-all
  [{:keys [versions] :as module-meta} request]
  (doseq [version versions]
    (when version
      (delete-content module-meta (:href version))))
  (delete-impl request))


(defn delete-item
  [module-meta {{full-uuid :uuid} :params :as request}]
  (let [version-index    (utils/latest-or-version-index module-meta full-uuid)
        content-id       (utils/get-content-id module-meta version-index)
        delete-response  (delete-content module-meta content-id)
        updated-versions (remove-version module-meta version-index)
        module-meta      (-> module-meta
                             (assoc :versions updated-versions)
                             (utils/set-published))]
    (edit-module module-meta
                 (update-in request [:params :uuid] utils/full-uuid->uuid)
                 "A failure happened during delete module item")

    delete-response))


(defmethod crud/delete resource-type
  [request]
  (try
    (let [module-meta (utils/retrieve-module-meta request)]
      (ectx/add-to-context :acl (:acl module-meta))
      (-> module-meta
          throw-project-cannot-delete-if-has-children
          (a/throw-cannot-edit request)
          (delete-all request)))
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
  [{:keys [id acl] :as _resource} request]
  (try
    (let [{{job-id     :resource-id
            job-status :status} :body} (job-utils/create-job id "validate-docker-compose"
                                                             acl (auth/current-user-id request)
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
  (let [resource (-> (str resource-type "/" uuid)
                     crud/retrieve-by-id-as-admin
                     (a/throw-cannot-manage request))]
    (if (utils/is-application? resource)
      (create-validate-docker-compose-job resource request)
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
;; Events
;;

(defmethod ec/events-enabled? resource-type
  [_resource-type]
  true)


(defmethod ec/log-event? "module.add"
  [_event _response]
  true)

(defmethod ec/log-event? "module.edit"
  [_event _response]
  true)

(defmethod ec/log-event? "module.delete"
  [_event _response]
  true)

(defmethod ec/log-event? "module.publish"
  [_event _response]
  true)

(defmethod ec/log-event? "module.unpublish"
  [_event _response]
  true)

(defmethod ec/log-event? "module.delete-version"
  [_event _response]
  true)

(defmethod ec/log-event? "module.deploy"
  [_event _response]
  true)


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::module/schema))

(defn initialize
  []
  (std-crud/initialize resource-type ::module/schema)
  (md/register resource-metadata)
  (utils/create-project-apps-sets))
