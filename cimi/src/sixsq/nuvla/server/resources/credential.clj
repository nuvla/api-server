(ns sixsq.nuvla.server.resources.credential
  "
SlipStream can manage credentials that are needed to access SlipStream or
other services. Currently, SlipStream manages SSH public keys and API keys and
secrets. Creating new Credential resources requires referencing a
CredentialTemplate resource.
"
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.log :as logu]))

(def ^:const resource-tag :credentials)

(def ^:const resource-name "Credential")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "CredentialCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def ^:const create-uri (str c/slipstream-schema-uri resource-name "Create"))

;; only authenticated users can view and create credentials
(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

;;
;; validate the created credential resource
;; must dispatch on the type because each credential has a different schema
;;

(defmulti validate-subtype :type)

(defmethod validate-subtype :default
  [resource]
  (logu/log-and-throw-400 (str "unknown Credential type: '" resource (:type resource) "'")))

(defmethod crud/validate resource-uri
  [resource]
  (validate-subtype resource))

;;
;; validate create requests for subclasses of credentials
;; different credentials take different inputs
;;

(defn dispatch-on-registration-method [resource]
  (get-in resource [:credentialTemplate :type]))

(defmulti create-validate-subtype dispatch-on-registration-method)

(defmethod create-validate-subtype :default
  [resource]
  (logu/log-and-throw-400 (str "cannot validate CredentialTemplate create document with type: '"
                               (dispatch-on-registration-method resource) "'")))

(defmethod crud/validate create-uri
  [resource]
  (create-validate-subtype resource))

;;
;; multimethod for ACLs
;;

(defn create-acl
  [id]
  {:owner {:principal "ADMIN"
           :type      "ROLE"}
   :rules [{:principal id
            :type      "USER"
            :right     "MODIFY"}]})

(defmethod crud/add-acl resource-uri
  [{:keys [acl] :as resource} request]
  (if acl
    resource
    (let [user-id (:identity (a/current-authentication request))]
      (assoc resource :acl (create-acl user-id)))))

;;
;; template processing
;;

(defn dispatch-conversion
  [resource _]
  (:type resource))

(defmulti tpl->credential dispatch-conversion)

;; default implementation throws if the credential type is unknown
(defmethod tpl->credential :default
  [resource request]
  (logu/log-and-throw-400
    (str "cannot transform CredentialTemplate document to template for type: '" (:type resource) "'")))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

;;
;; available operations
;;

;; Use standard method for setting operations.
#_(defmethod crud/set-operations resource-uri
    [resource request]
    (try
      (a/can-modify? resource request)
      (let [href (:id resource)
            ^String resourceURI (:resourceURI resource)
            ops (if (.endsWith resourceURI "Collection")
                  [{:rel (:add c/action-uri) :href href}]
                  [{:rel (:delete c/action-uri) :href href}])]
        (assoc resource :operations ops))
      (catch Exception e
        (dissoc resource :operations))))

(defn check-connector-exists
  "Use ADMIN role as we only want to check if href points to an existing
  resource."
  [body idmap]
  (let [admin {:identity {:current         "internal",
                          :authentications {"internal" {:roles #{"ADMIN"}, :identity "internal"}}}}
        href (get-in body [:credentialTemplate :connector])]
    (std-crud/resolve-hrefs href admin))
  body)

(defn resolve-hrefs
  [body idmap]
  (let [connector-href (if (contains? (:credentialTemplate body) :connector)
                         {:connector (get-in body [:credentialTemplate :connector])}
                         {})]                               ;; to put back the unexpanded href after
    (-> body
        (check-connector-exists idmap)
        ;; remove connector href (if any); regular user doesn't have rights to see them
        (update-in [:credentialTemplate] dissoc :connector)
        (std-crud/resolve-hrefs idmap)
        ;; put back unexpanded connector href
        (update-in [:credentialTemplate] merge connector-href))))

;; requires a CredentialTemplate to create new Credential
(defmethod crud/add resource-name
  [{:keys [body] :as request}]
  (let [idmap {:identity (:identity request)}
        desc-attrs (u/select-desc-keys body)
        [create-resp {:keys [id] :as body}]
        (-> body
            (assoc :resourceURI create-uri)
            (update-in [:credentialTemplate] dissoc :type)  ;; forces use of template reference
            (resolve-hrefs idmap)
            (update-in [:credentialTemplate] merge desc-attrs) ;; ensure desc attrs are validated
            crud/validate
            :credentialTemplate
            (tpl->credential request))]
    (-> request
        (assoc :id id :body (merge body desc-attrs))
        add-impl
        (update-in [:body] merge create-resp))))

(defmulti special-edit dispatch-conversion)

(defmethod special-edit :default
  [resource _]
  resource)

(def edit-impl (std-crud/edit-fn resource-name))
(defmethod crud/edit resource-name
  [{{uuid :uuid} :params body :body :as request}]
  (let [type (-> (str (u/de-camelcase resource-name) "/" uuid)
                 (db/retrieve request)
                 :type)
        new-body (-> body
                     (assoc :type type)
                     (special-edit request))]
    (edit-impl (assoc request :body new-body))))

(def retrieve-impl (std-crud/retrieve-fn resource-name))
(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))

(def delete-impl (std-crud/delete-fn resource-name))
(defmethod crud/delete resource-name
  [request]
  (delete-impl request))

(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))
(defmethod crud/query resource-name
  [request]
  (query-impl request))


;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize resource-url nil))
