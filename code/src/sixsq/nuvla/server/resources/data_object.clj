(ns sixsq.nuvla.server.resources.data-object
  (:require
    [buddy.core.codecs :as co]
    [buddy.core.hash :as ha]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [ring.util.response :as ru]
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-object-template :as dot]
    [sixsq.nuvla.server.resources.data-object.utils :as s3]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})


(def ^:const state-new "NEW")


(def ^:const state-uploading "UPLOADING")


(def ^:const state-ready "READY")


;;
;; validate subclasses of data-object
;;

(defmulti validate-subtype
          :type)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown External object type: '" (:type resource) "'") resource)))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))

;;
;; validate create requests for subclasses of data objects
;;

(defn dispatch-on-type [resource]
  (get-in resource [:template :type]))


(defmulti create-validate-subtype dispatch-on-type)


(defmethod create-validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown External Object create type: " (dispatch-on-type resource) resource) resource)))


(defmethod crud/validate create-type
  [resource]
  (create-validate-subtype resource))

;;
;; multimethods for validation for the data objects
;;

(defmulti validate-subtype
          "Validates the given resource against the specific
           DataObjectTemplate subtype schema."
          :type)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown DataObjectTemplate type: " (:type resource)) resource)))


(defmethod crud/validate
  resource-type
  [resource]
  (validate-subtype resource))

;;
;; multimethod for ACLs
;;

(defn create-acl [id]
  {:owner {:principal "ADMIN"
           :type      "ROLE"}
   :rules [{:principal id
            :type      "USER"
            :right     "MODIFY"}]})


(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


(defmethod crud/add-acl resource-type
  [{:keys [acl] :as resource} request]
  (if acl
    resource
    (let [user-id (:identity (a/current-authentication request))]
      (assoc resource :acl (create-acl user-id)))))


(defn standard-data-object-collection-operations
  [{:keys [id] :as resource} request]
  (when (a/authorized-modify? resource request)
    [{:rel (:add c/action-uri) :href id}]))


(defn standard-data-object-resource-operations
  [{:keys [id state] :as resource} request]
  (let [viewable? (a/authorized-view? resource request)
        modifiable? (a/authorized-modify? resource request)
        show-upload-op? (and modifiable? (#{state-new state-uploading} state))
        show-ready-op? (and modifiable? (#{state-uploading} state))
        show-download-op? (and viewable? (#{state-ready} state))
        ops (cond-> []
                    modifiable? (conj {:rel (:delete c/action-uri) :href id})
                    modifiable? (conj {:rel (:edit c/action-uri) :href id})
                    show-upload-op? (conj {:rel (:upload c/action-uri) :href (str id "/upload")})
                    show-ready-op? (conj {:rel (:ready c/action-uri) :href (str id "/ready")})
                    show-download-op? (conj {:rel (:download c/action-uri) :href (str id "/download")}))]
    (when (seq ops)
      (vec ops))))


(defn standard-data-object-operations
  "Provides a list of the standard data object operations, depending
   on the user's authentication and whether this is a DataObject or
   a DataObjectCollection."
  [{:keys [resource-type] :as resource} request]
  (if (u/is-collection? resource-type)
    (standard-data-object-collection-operations resource request)
    (standard-data-object-resource-operations resource request)))


(defmethod crud/set-operations resource-type
  [resource request]
  (let [ops (standard-data-object-operations resource request)]
    (cond-> (dissoc resource :operations)
            (seq ops) (assoc :operations ops))))


;;
;; Generate ID.
;;

(defmethod crud/new-identifier resource-type
  [{:keys [object bucket] :as resource} resource-name]
  (if-let [new-id (co/bytes->hex (ha/md5 (str object bucket)))]
    (assoc resource :id (str resource-name "/" new-id))))

;;
;; template processing
;;

(defmulti tpl->data-object
          "Transforms the DataObjectTemplate into a DataObject resource."
          :type)

;; default implementation just updates the resource-type

(defmethod tpl->data-object :default
  [resource]
  (assoc resource :resource-type resource-type))

;;
;; CRUD operations
;;

(defn check-cred-exists
  [body idmap]
  (let [href (get-in body [:template :credential])]
    (std-crud/resolve-hrefs href idmap))
  body)

(defn resolve-hrefs
  [body idmap]
  (let [os-cred-href (if (contains? (:template body) :credential)
                       {:credential (get-in body [:template :credential])}
                       {})]                                 ;; to put back the unexpanded href after
    (-> body
        (check-cred-exists idmap)
        ;; remove connector href (if any); regular user MAY NOT have rights to see it
        (update-in [:template] dissoc :credential)
        (std-crud/resolve-hrefs idmap)
        ;; put back unexpanded connector href
        (update-in [:template] merge os-cred-href))))


(defn merge-into-tmpl
  [body]
  (if-let [href (get-in body [:template :href])]
    (let [tmpl (-> (get @dot/templates href)
                   u/strip-service-attrs
                   u/strip-common-attrs
                   (dissoc :acl))
          user-resource (-> body
                            :template
                            (dissoc :href))]
      (assoc-in body [:template] (merge tmpl user-resource)))
    body))

;; requires a DataObjectTemplate to create new DataObject

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{:keys [body] :as request}]
  (a/can-modify? {:acl collection-acl} request)
  (let [idmap {:identity (:identity request)}
        body (-> body
                 (assoc :resource-type create-type)
                 (merge-into-tmpl)
                 (resolve-hrefs idmap)
                 (crud/validate)
                 :template
                 (tpl->data-object)
                 (assoc :state state-new))]
    (s3/ensure-bucket-exists body request)
    (add-impl (assoc request :body body))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


;; editing is special as only a few attributes can be modified

(defn select-editable-attributes
  [body]
  (select-keys body #{:name :description :tags :acl}))

(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{:keys [body] :as request}]
  (edit-impl (assoc request :body (select-editable-attributes body))))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))

;;; Generic utilities for actions

(defn format-states
  [states]
  (->> states
       (map #(format "'%s'" %))
       (str/join ", ")))


(defn error-msg-bad-state
  [action required-states current-state]
  (format "Invalid state '%s' for '%s' action. Valid states: %s."
          current-state action (format-states required-states)))


(defn verify-state
  [{:keys [state] :as resource} accepted-states action]
  (if (accepted-states state)
    resource
    (logu/log-and-throw-400 (error-msg-bad-state action accepted-states state))))


;;; Upload URL operation

(defn upload-fn
  "Provided 'resource' and 'request', returns object storage upload URL.
  It is assumed that the bucket already exists and the user has access to it."
  [{:keys [type content-type bucket object credential runUUID filename] :as resource} {{ttl :ttl} :body :as request}]
  (verify-state resource #{state-new state-uploading} "upload")
  (let [object (if (not-empty object)
                 object
                 (format "%s/%s" runUUID filename))
        obj-store-conf (s3/credential->s3-client-cfg credential)]
    (log/info "Requesting upload url:" object)
    (s3/generate-url obj-store-conf bucket object :put
                     {:ttl (or ttl s3/default-ttl) :content-type content-type :filename filename})))


(defn upload
  [resource request]
  (try
    (a/can-modify? resource request)
    (let [upload-uri (upload-fn resource request)]
      (db/edit (assoc resource :state state-uploading) request)
      (r/json-response {:uri upload-uri}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "upload"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (upload (crud/retrieve-by-id-as-admin id) request))
    (catch Exception e
      (or (ex-data e) (throw e)))))



(defmulti ready-subtype
          (fn [resource _] (:type resource)))

(defmethod ready-subtype :default
  [resource request]
  (-> resource
      (a/can-modify? request)
      (verify-state #{state-uploading} "ready")
      (assoc :state state-ready)
      (s3/add-s3-size)
      (s3/add-s3-md5sum)
      (db/edit request)))

(defmethod crud/do-action [resource-type "ready"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [resource (crud/retrieve-by-id-as-admin (str resource-type "/" uuid))]
      (ready-subtype resource request))
    (catch Exception e
      (or (ex-data e) (throw e)))))

;;; Download URL operation

(defmulti download-subtype
          "Provided 'resource' and 'request', returns object storage download URL."
          (fn [resource _] (:type resource)))



(defmethod download-subtype :default
  [{:keys [bucket object credential] :as resource} {{ttl :ttl} :body :as request}]
  (verify-state resource #{state-ready} "download")
  (log/info "Requesting download url: " object)
  (s3/generate-url (s3/credential->s3-client-cfg credential)
                   bucket object :get
                   {:ttl (or ttl s3/default-ttl)}))


(defn download
  [resource request]
  (let [dl-uri (download-subtype resource request)]
    (try
      (-> {:status 303
           :body   {:uri dl-uri}}
          (ru/header "Location" dl-uri)
          (ru/header "Content-Type" "application/json"))
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-type "download"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (crud/retrieve-by-id-as-admin id)
          (a/can-view? request)
          (download request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))

;;; Delete resource.

(def delete-impl (std-crud/delete-fn resource-type))


(defn delete
  [{:keys [object bucket credential] :as resource}
   {{keep-object? :keep-s3-object, keep-bucket? :keep-s3-bucket} :body :as request}]
  (when-not keep-object?
    (try
      (s3/try-delete-s3-object (s3/credential->s3-client-cfg credential) bucket object)
      (log/infof "object %s from bucket %s has been deleted" object bucket)
      (catch Exception e
        ;; When the user requests to delete an S3 object that no longer exists,
        ;; the data object resource should be deleted normally.
        ;; Ignore 404 exceptions.
        (let [status (:status (ex-data e))]
          (when-not (= 404 status)
            (throw e))))))
  ;; Always try to remove the bucket to avoid having unused empty buckets.
  ;; Request will fail when the bucket isn't empty.  These errors are ignored.
  (when-not keep-bucket?
    (try
      (s3/try-delete-s3-bucket (s3/credential->s3-client-cfg credential) bucket)
      (log/debugf "bucket %s became empty and was deleted" bucket)
      (catch Exception _)))
  (delete-impl request))


(defmethod crud/delete resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (crud/retrieve-by-id-as-admin id)
          (a/can-modify? request)
          (delete request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; initialization: no schema for the parent
;;

(defn initialize
  []
  (std-crud/initialize resource-type nil))
