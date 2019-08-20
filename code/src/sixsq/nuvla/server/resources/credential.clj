(ns sixsq.nuvla.server.resources.credential
  "
Nuvla can manage credentials that are needed to access Nuvla (e.g. hashed user
passwords) or other services (e.g. TLS credentials for Docker). Creating new
`credential` resources requires referencing a `credential-template` resource.
"
  (:require
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.log :as logu]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


;; only authenticated users can view and create credentials
(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; initialization: no schema for this parent resource
;;

(defn initialize
  []
  (std-crud/initialize resource-type nil))


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
    (let [user-id (auth/current-user-id request)]
      (assoc resource :acl (create-acl user-id)))))


;;
;; template processing
;;

(defn dispatch-conversion
  [resource _]
  (:subtype resource))


(defmulti tpl->credential dispatch-conversion)


;; default implementation throws if the credential subtype is unknown
(defmethod tpl->credential :default
  [resource request]
  (logu/log-and-throw-400
    (str "cannot transform credential-template document to template for subtype: '" (:subtype resource) "'")))


;;
;; CRUD operations
;;

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
  [body authn-info]
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
        [create-resp {:keys [id] :as body}]
        (-> body
            (assoc :resource-type create-type)
            (update-in [:template] dissoc :subtype)         ;; forces use of template reference
            (resolve-hrefs authn-info)
            (update-in [:template] merge desc-attrs)        ;; ensure desc attrs are validated
            crud/validate
            :template
            (tpl->credential request))]
    (-> request
        (assoc :id id :body (merge body desc-attrs))
        add-impl
        (update-in [:body] merge create-resp))))


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


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{{uuid :uuid} :params body :body :as request}]
  (let [subtype  (-> (str resource-type "/" uuid)
                     (db/retrieve request)
                     :subtype)
        new-body (-> body
                     (assoc :subtype subtype)
                     (special-edit request))]
    (edit-impl (assoc request :body new-body))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))
(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))
(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))
(defmethod crud/query resource-type
  [request]
  (query-impl request))
