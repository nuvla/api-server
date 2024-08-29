(ns com.sixsq.nuvla.server.resources.nuvlabox-peripheral
  "
The nuvlabox-peripheral resource represents a peripheral attached to a
nuvlabox.
"
  (:require
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-peripheral :as nb-peripheral]
    [com.sixsq.nuvla.server.util.log :as logu]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:add   ["group/nuvla-user"]
                     :query ["group/nuvla-user"]})


(defn has-video-capability?
  [resource]
  (contains? resource :video-device))


(defn create-job
  [{:keys [id parent]} request action]
  (try
    (let [authn-info (auth/current-authentication request)
          {{job-id     :resource-id
            job-status :status} :body} (job-utils/create-job
                                         id action
                                         (if (a/is-admin? authn-info)
                                           {:owners ["group/nuvla-admin"]}
                                           {:owners   ["group/nuvla-admin"]
                                            :edit-acl [(auth/current-active-claim request)]})
                                         (auth/current-user-id request)
                                         :priority 50
                                         :affected-resources [{:href id}
                                                              {:href parent}])
          job-msg    (str "starting " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response
                 (format "unable to create async job to %s" action) 500 id)))
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn throw-data-gateway-already-enabled
  [{:keys [data-gateway-enabled] :as resource}]
  (if data-gateway-enabled
    (logu/log-and-throw-400 "NuvlaBox peripheral data gateway already enabled!")
    resource))


(defn throw-data-gateway-already-disabled
  [{:keys [data-gateway-enabled] :as resource}]
  (if data-gateway-enabled
    resource
    (logu/log-and-throw-400 "NuvlaBox peripheral data gateway already disabled!")))


(defn throw-doesnt-have-video-capability
  [resource]
  (if (has-video-capability? resource)
    resource
    (logu/log-and-throw-400 "NuvlaBox peripheral does not have video capability!")))


(defmethod crud/do-action [resource-type "enable-stream"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (utils/throw-parent-nuvlabox-is-suspended)
        (throw-doesnt-have-video-capability)
        (throw-data-gateway-already-enabled)
        (create-job request "enable-stream"))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "disable-stream"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (a/throw-cannot-manage request)
        (throw-data-gateway-already-disabled)
        (throw-doesnt-have-video-capability)
        (create-job request "disable-stream"))
    (catch Exception e
      (or (ex-data e) (throw e)))))

;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given nuvlabox-peripheral resource against a specific
           version of the schema."
          :version)


(defmethod validate-subtype :default
  [{:keys [version] :as _resource}]
  (if version
    (throw (r/ex-bad-request (str "unsupported nuvlabox-peripheral version: " version)))
    (throw (r/ex-bad-request "missing nuvlabox-peripheral version"))))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))

;;
;; acl
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (if-let [nuvlabox-id (:parent resource)]
    (let [{nuvlabox-acl :acl} (crud/retrieve-by-id-as-admin nuvlabox-id)
          view-acl (:view-acl nuvlabox-acl)]
      (assoc resource
        :acl (cond-> (utils/set-acl-nuvlabox-view-only nuvlabox-acl {:owners [nuvlabox-id]})
                     (not-empty view-acl) (assoc :manage view-acl))))
    (a/add-acl resource request)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{:keys [body] :as request}]
  (-> body
      :parent
      (crud/retrieve-by-id-as-admin)
      (a/can-view? request)
      (utils/throw-nuvlabox-is-suspended))
  (add-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id       (str resource-type "/" uuid)
          resource (crud/retrieve-by-id-as-admin id)]
      (when (and (has-video-capability? body) (:data-gateway-enabled resource))
        (-> resource
            (a/throw-cannot-manage request)
            (utils/throw-parent-nuvlabox-is-suspended)
            (create-job request "restart-stream"))))
    (catch Exception e
      (or (ex-data e) (throw e))))
  (edit-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)
          {:keys [data-gateway-enabled] :as resource} (crud/retrieve-by-id-as-admin id)]

      (when (and data-gateway-enabled (has-video-capability? resource))
        (create-job resource request "disable-stream")))
    (catch Exception e
      (or (ex-data e) (throw e))))
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; Set operation
;;



(defmethod crud/set-operations resource-type
  [{:keys [id data-gateway-enabled] :as resource} request]
  (let [enable-stream-op  (u/action-map id :enable-stream)
        disable-stream-op (u/action-map id :disable-stream)
        can-manage?       (a/can-manage? resource request)
        has-video?        (has-video-capability? resource)]
    (cond-> (crud/set-standard-operations resource request)
            (and can-manage?
                 has-video?
                 (< (:version resource) 2)) (update :operations conj (if data-gateway-enabled
                                                                       disable-stream-op
                                                                       enable-stream-op)))))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-peripheral/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nb-peripheral/schema)
  (md/register resource-metadata))
