(ns sixsq.nuvla.server.resources.user
  "
User resources contain personal information and a few parameters concerning
the registered users. This is a templated resource, so creating a new user
requires a template. All the SCRUD actions follow the standard CIMI patterns.
"
  (:require
    [clj-time.core :as t]
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.resources.user-template-direct :as tpl]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.util.response :as r]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const resource-name "User")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "UserCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def ^:const create-uri (str c/slipstream-schema-uri resource-name "Create"))

(def ^:const form-urlencoded "application/x-www-form-urlencoded")

;; creating a new user is a registration request, so anonymous users must
;; be able to view the collection and post requests to it (if a template is
;; visible to ANON.)
(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "ANON"
                              :type      "ROLE"
                              :right     "MODIFY"}]})


;;
;; common validation for created users
;;

(def validate-fn (u/create-spec-validation-fn ::user/schema))
(defmethod crud/validate resource-uri
  [resource]
  (validate-fn resource))

;;
;; validate create requests for subclasses of users
;; different create (registration) requests may take different inputs
;;
(defn dispatch-on-registration-method [resource]
  (get-in resource [:template :method]))

(defmulti create-validate-subtype dispatch-on-registration-method)

(defmethod create-validate-subtype :default
  [resource]
  (logu/log-and-throw-400 "missing or invalid UserTemplate reference"))

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
   :rules [{:principal "ADMIN"
            :type      "ROLE"
            :right     "ALL"}
           {:principal id
            :type      "USER"
            :right     "MODIFY"}]})

(defmethod crud/add-acl resource-uri
  [{:keys [username acl] :as resource} request]
  (assoc
    resource
    :acl
    (or acl (create-acl username))))

;;
;; set the resource identifier to "user/username"
;;
(defmethod crud/new-identifier resource-name
  [{:keys [username] :as json} resource-name]
  (assoc json :id (str resource-url "/" username)))

;;
;; template processing
;;

(defn dispatch-conversion
  [resource _]
  (:method resource))

;; transforms the user template into a user resource
;;
;; The concrete implementation of this method MUST return a two-element
;; tuple containing a response fragment and the created user resource.
;; The response fragment will be merged with the 'add-impl' function
;; response and should be used to override the return status (e.g. to
;; instead provide a redirect) and to set a cookie header.
;;
(defmulti tpl->user dispatch-conversion)

; All concrete session types MUST provide an implementation of this
;; multimethod. The default implementation will throw an 'internal
;;; server error' exception.
;;
(defmethod tpl->user :default
  [resource request]
  [{:status 400, :message "missing or invalid UserTemplate reference"} nil])


;; handles any actions that must be taken after the user is added
(defmulti post-user-add dispatch-conversion)

;; default implementation is a no-op
(defmethod post-user-add :default
  [resource request]
  nil)

;;
;; CRUD operations
;;

;; Some defaults for the optional attributes.
(def ^:const epoch (u/unparse-timestamp-datetime (t/date-time 1970)))

(def ^:const initial-state "NEW")

(def user-attrs-defaults
  {:state       initial-state
   :deleted     false})

(defn merge-with-defaults
  [resource]
  (merge user-attrs-defaults resource))

(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

(defn revert-method
  [[fragment {:keys [method] :as body}] original-method]
  (if (and original-method (= tpl/registration-method method))
    [fragment (assoc body :method original-method)]
    [fragment body]))

(defn merge-attrs
  [[fragment m] desc-attrs]
  [fragment (merge m desc-attrs)])

;; requires a UserTemplate to create new User
(defmethod crud/add resource-name
  [{:keys [body form-params headers] :as request}]

  (try
    (let [idmap {:identity (:identity request)}
          body (if (u/is-form? headers) (u/convert-form :template form-params) body)
          authn-method (-> body :template :method)
          desc-attrs (u/select-desc-keys body)
          [resp-fragment {:keys [id] :as body}] (-> body
                                                    (assoc :resource-type create-uri)
                                                    (update-in [:template] dissoc :method :id) ;; forces use of template reference
                                                    (std-crud/resolve-hrefs idmap true)
                                                    (update-in [:template] merge desc-attrs) ;; validate desc attrs
                                                    (crud/validate)
                                                    (:template)
                                                    (merge-with-defaults)
                                                    (tpl->user request) ;; returns a tuple [response-fragment, resource-body]
                                                    (merge desc-attrs)
                                                    (revert-method authn-method))]

      (cond

        ;; pure redirect that hasn't created a user account
        (and resp-fragment (nil? body)) resp-fragment

        ;; requested redirect with method that created a user
        (and resp-fragment body) (let [{{:keys [status resource-id]} :body :as result} (add-impl (assoc request :id id :body body))]
                                   (when (and resource-id (= 201 status))
                                     (post-user-add (assoc body :id resource-id) request))
                                   (cond-> resp-fragment
                                           resource-id (assoc-in [:body :resource-id] resource-id)))

        ;; normal case: no redirect and user was created
        :else (let [{{:keys [status resource-id]} :body :as result} (add-impl (assoc request :id id :body body))]
                (when (and resource-id (= 201 status))
                  (post-user-add (assoc body :id resource-id) request))
                result)))
    (catch Exception e
      (let [redirectURI (get-in body [:template :redirectURI])
            {:keys [status] :as http-response} (ex-data e)]
        (if (and redirectURI (= 400 status))
          (throw (r/ex-redirect (str "invalid parameter values provided") nil redirectURI))
          (or http-response (throw e)))))))

(def retrieve-impl (std-crud/retrieve-fn resource-name))
(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))

(def delete-impl (std-crud/delete-fn resource-name))
(defmethod crud/delete resource-name
  [request]
  (delete-impl request))

(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri))
(defmethod crud/query resource-name
  [request]
  (query-impl request))

(defn in?
  "true if coll contains elm."
  [coll elm]
  (if (some #(= elm %) coll) true false))

(defn admin?
  "Expects identity map from the request."
  [identity]
  (-> identity
      :authentications
      (get (:current identity))
      :roles
      (in? "ADMIN")))

(defn filter-for-regular-user
  [user-resource request]
  (if (admin? (:identity request))
    user-resource
    (dissoc user-resource :isSuperUser)))

(defn throw-no-id
  [body]
  (if-not (contains? body :id)
    (logu/log-and-throw-400 "id is not provided in the document.")))

(defn edit-impl [{body :body :as request}]
  "Returns edited document or exception data in case of an error."
  (throw-no-id body)
  (try
    (let [current (-> (:id body)
                      (db/retrieve request)
                      (a/can-modify? request))
          merged (merge current (filter-for-regular-user body request))]
      (-> merged
          (dissoc :href)
          (u/update-timestamps)
          (crud/validate)
          (db/edit request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))
(defmethod crud/edit resource-name
  [request]
  (edit-impl request))


;;
;; initialization: common schema for all user creation methods
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::user/schema))
