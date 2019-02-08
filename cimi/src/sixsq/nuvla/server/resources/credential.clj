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
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.log :as logu]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


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

(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))

;;
;; validate create requests for subclasses of credentials
;; different credentials take different inputs
;;

(defn dispatch-on-registration-method [resource]
  (get-in resource [:template :type]))

(defmulti create-validate-subtype dispatch-on-registration-method)

(defmethod create-validate-subtype :default
  [resource]
  (logu/log-and-throw-400 (str "cannot validate CredentialTemplate create document with type: '"
                               (dispatch-on-registration-method resource) "'")))

(defmethod crud/validate create-type
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

(defmethod crud/add-acl resource-type
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

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

;;
;; available operations
;;

;; Use standard method for setting operations.
#_(defmethod crud/set-operations resource-type
    [resource request]
    (try
      (a/can-modify? resource request)
      (let [href (:id resource)
            ^String resource-type (:resource-type resource)
            ops (if (is-collection? resource-type)
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
        href (get-in body [:template :connector])]
    (std-crud/resolve-hrefs href admin))
  body)

(defn resolve-hrefs
  [body idmap]
  (let [connector-href (if (contains? (:template body) :connector)
                         {:connector (get-in body [:template :connector])}
                         {})]                               ;; to put back the unexpanded href after
    (-> body
        (check-connector-exists idmap)
        ;; remove connector href (if any); regular user doesn't have rights to see them
        (update-in [:template] dissoc :connector)
        (std-crud/resolve-hrefs idmap)
        ;; put back unexpanded connector href
        (update-in [:template] merge connector-href))))

;; requires a CredentialTemplate to create new Credential
(defmethod crud/add resource-type
  [{:keys [body] :as request}]
  (let [idmap {:identity (:identity request)}
        desc-attrs (u/select-desc-keys body)
        [create-resp {:keys [id] :as body}]
        (-> body
            (assoc :resource-type create-type)
            (update-in [:template] dissoc :type)  ;; forces use of template reference
            (resolve-hrefs idmap)
            (update-in [:template] merge desc-attrs) ;; ensure desc attrs are validated
            crud/validate
            :template
            (tpl->credential request))]
    (-> request
        (assoc :id id :body (merge body desc-attrs))
        add-impl
        (update-in [:body] merge create-resp))))

(defmulti special-edit dispatch-conversion)

(defmethod special-edit :default
  [resource _]
  resource)

(def edit-impl (std-crud/edit-fn resource-type))
(defmethod crud/edit resource-type
  [{{uuid :uuid} :params body :body :as request}]
  (let [type (-> (str resource-type "/" uuid)
                 (db/retrieve request)
                 :type)
        new-body (-> body
                     (assoc :type type)
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


;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize resource-type nil))
