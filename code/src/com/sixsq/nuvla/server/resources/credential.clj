(ns com.sixsq.nuvla.server.resources.credential
  "
Nuvla can manage credentials that are needed to access Nuvla (e.g. hashed user
passwords) or other services (e.g. TLS credentials for Docker). Creating new
`credential` resources requires referencing a `credential-template` resource.
"
  (:require
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.credential :as credential]
    [com.sixsq.nuvla.server.util.log :as logu]
    [com.sixsq.nuvla.server.resources.credential.encrypt-utils :as eu]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]
    [com.sixsq.nuvla.server.util.time :as time]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


;; only authenticated users can view and create credentials
(def collection-acl {:query ["group/nuvla-user"
                             "group/nuvla-nuvlabox"]
                     :add   ["group/nuvla-user"
                             "group/nuvla-nuvlabox"]})


(def resource-metadata (gen-md/generate-metadata ::ns ::credential/schema))


;;
;; initialization: no schema for this parent resource
;;

(def initialization-order 110)

(defn initialize
  []
  (std-crud/initialize resource-type ::credential/schema)
  (md/register resource-metadata))


;;
;; validate the created credential resource
;; must dispatch on the subtype because each credential has a different schema
;;

(defmulti validate-subtype :subtype)


(defmethod validate-subtype :default
  [resource]
  (logu/log-and-throw-400 (str "unknown Credential subtype: '" resource (:subtype resource) "'")))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))


;;
;; validate create requests for subclasses of credentials
;; different credentials take different inputs
;;

(defn dispatch-on-registration-method [resource]
  (get-in resource [:template :subtype]))


(defmulti create-validate-subtype dispatch-on-registration-method)


(defmethod create-validate-subtype :default
  [resource]
  (logu/log-and-throw-400 (str "cannot validate CredentialTemplate create document with subtype: '"
                               (dispatch-on-registration-method resource) "'")))


(defmethod crud/validate create-type
  [resource]
  (create-validate-subtype resource))

;;
;; multimethod for ACLs
;;

(defn create-acl
  [id]
  {:owners   ["group/nuvla-admin"]
   :edit-acl [id]})


(defmethod crud/add-acl resource-type
  [{:keys [acl] :as resource} request]
  (if acl
    resource
    (let [active-claim (auth/current-active-claim request)]
      (assoc resource :acl (create-acl active-claim)))))


;;
;; template processing
;;

(defn dispatch-conversion
  [resource _]
  (:subtype resource))


(defmulti tpl->credential dispatch-conversion)


;; default implementation throws if the credential subtype is unknown
(defmethod tpl->credential :default
  [resource _request]
  (logu/log-and-throw-400
    (str "cannot transform credential-template document to template for subtype: '"
         (:subtype resource) "'")))

;;
;; actions
;;

(defn create-check-credential-job
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (if-let [active-claim (auth/current-active-claim request)]
        (let [job-type "credential_check"
              {{job-id     :resource-id
                job-status :status} :body} (job-utils/create-job id job-type
                                                                 {:owners   ["group/nuvla-admin"]
                                                                  :view-acl [active-claim]}
                                                                 (auth/current-user-id request)
                                                                 :priority 50)
              job-msg  (str "starting " id " with async " job-id)]
          (when (not= job-status 201)
            (throw (r/ex-response (format "unable to create async job to %s" job-type) 500 id)))
          (r/map-response job-msg 202 id job-id))
        (throw (r/ex-response "current authentication has no session identifier" 500 id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defn create-check-credential-request
  [id request]
  (create-check-credential-job
    {:params      {:uuid          (u/id->uuid id)
                   :resource-name resource-type}
     :nuvla/authn (auth/current-authentication request)}))


(defmethod crud/do-action [resource-type "check"]
  [{{uuid :uuid} :params :as request}]
  (let [id       (str resource-type "/" uuid)
        resource (crud/retrieve-by-id-as-admin id)]
    (a/throw-cannot-manage resource request)
    (create-check-credential-job request)))


;;
;; CRUD operations
;;

(def dispatch-by-first-arg-method
  (fn [{:keys [method] :as _resource} _request] method))

(defmulti post-add-hook dispatch-by-first-arg-method)

(defmethod post-add-hook :default
  [_resource _request]
  nil)

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


;;
;; Credentials can have their own actions, dispatch to subtypes
;; to allow them to set their own.  Defaults to setting the standard
;; operations.
;;

(defmulti set-credential-operations dispatch-conversion)


(defmethod set-credential-operations :default
  [resource request]
  (crud/set-standard-operations resource request))


(defmethod crud/set-operations resource-type
  [resource request]
  (set-credential-operations resource request))


;; FIXME: Update this for services.

(defn check-connector-exists
  "Use ADMIN role as we only want to check if href points to an existing
  resource."
  [body _authn-info]
  (let [href (get-in body [:template :connector])]
    (std-crud/resolve-hrefs href auth/internal-identity))
  body)


(defn resolve-hrefs
  [body authn-info]
  (let [connector-href (if (contains? (:template body) :connector)
                         {:connector (get-in body [:template :connector])}
                         {})]                               ;; to put back the unexpanded href after
    (-> body
        (check-connector-exists authn-info)
        ;; remove connector href (if any); regular user doesn't have rights to see them
        (update-in [:template] dissoc :connector)
        (std-crud/resolve-hrefs authn-info)
        ;; put back unexpanded connector href
        (update-in [:template] merge connector-href))))


;; requires a credential-template to create new credential
(defmethod crud/add resource-type
  [{:keys [body] :as request}]
  (let [authn-info (auth/current-authentication request)
        desc-attrs (u/select-desc-keys body)
        [create-resp body]
        (-> body
            (assoc :resource-type create-type)
            (update-in [:template] dissoc :subtype)         ;; forces use of template reference
            (resolve-hrefs authn-info)
            (update-in [:template] merge desc-attrs)        ;; ensure desc attrs are validated
            crud/validate
            :template
            (tpl->credential request))

        response   (-> request
                       (assoc :body (merge body desc-attrs))
                       eu/encrypt-request-body-secrets
                       add-impl
                       (update-in [:body] merge create-resp))

        id         (-> response :body :resource-id)
        cred       (assoc body :id id)]

    (post-add-hook cred request)

    response))


(defn create-credential
  "Utility to facilitate creating a new credential resource. The returned value
   is the standard 'add' response for the request."
  [credential-template identity]
  (let [credential-request {:params      {:resource-name resource-type}
                            :nuvla/authn identity
                            :body        credential-template}]
    (crud/add credential-request)))


(defmulti special-edit dispatch-conversion)


(defmethod special-edit :default
  [resource _]
  resource)


(defmulti special-delete dispatch-conversion)


(def delete-impl (std-crud/delete-fn resource-type))
(defmethod special-delete :default
  [_ request]
  (delete-impl request))


(def edit-impl (std-crud/edit-fn resource-type))

(defmethod crud/edit resource-type
  [{{uuid :uuid} :params body :body :as request}]
  (let [cred-subtype-iv (-> (str resource-type "/" uuid)
                            crud/retrieve-by-id-as-admin
                            (select-keys [:subtype :initialization-vector]))]
    (-> body
        (merge cred-subtype-iv)
        (dissoc :last-check)
        (cond-> (:status body) (assoc :last-check (time/now-str)))
        (special-edit request)
        (->> (assoc request :body)
             eu/encrypt-request-body-secrets
             (edit-impl)
             eu/decrypt-response-body-secrets))))


(defn update-credential
  [id body identity]
  (let [request {:params      {:uuid          (u/id->uuid id)
                               :resource-name resource-type}
                 :nuvla/authn identity
                 :body        body}]
    (crud/edit request)))

(defmethod crud/retrieve-by-id resource-type
  [id & [request]]
  (-> (crud/retrieve-by-id-default id request)
      eu/decrypt-credential-secrets))

(def retrieve-impl (std-crud/retrieve-fn resource-type))
(defmethod crud/retrieve resource-type
  [request]
  (-> (retrieve-impl request)
      eu/decrypt-response-body-secrets))


(defmethod crud/delete resource-type
  [{{uuid :uuid} :params :as request}]
  (-> (str resource-type "/" uuid)
      crud/retrieve-by-id-as-admin
      (a/throw-cannot-delete request)
      (special-delete request)))

(defmethod crud/query-collection resource-type
  [collection-id options]
  (let [[metadata entries] (crud/query-collection-default collection-id options)]
    [metadata (if eu/ENCRYPTION-KEY
                (map eu/decrypt-credential-secrets entries)
                entries)]))

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))

(defmethod crud/query resource-type
  [request]
  (eu/decrypt-response-query-credentials (query-impl request)))
