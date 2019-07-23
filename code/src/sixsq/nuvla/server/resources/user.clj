(ns sixsq.nuvla.server.resources.user
  "
User resources contain personal information and a few parameters concerning
the registered users. This is a templated resource, so creating a new user
requires a template. All the SCRUD actions follow the standard CIMI patterns.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.password :as password]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.email :as email]
    [sixsq.nuvla.server.resources.group :as group]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [sixsq.nuvla.server.resources.user-template :as p]
    [sixsq.nuvla.server.resources.user-template-username-password :as username-password]
    [sixsq.nuvla.server.util.log :as logu]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def ^:const form-urlencoded "application/x-www-form-urlencoded")


;; creating a new user is a registration request, so anonymous users must
;; be able to view the collection and post requests to it (if a template is
;; visible to group/nuvla-anon.)

(def collection-acl {:query ["group/nuvla-anon"]
                     :add   ["group/nuvla-anon"]})


;;
;; common validation for created users
;;

(def validate-fn (u/create-spec-validation-fn ::user/schema))
(defmethod crud/validate resource-type
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
  (let [method (dispatch-on-registration-method resource)]
    (logu/log-and-throw-400 (format "invalid user registration method '%s'" method))))


(defmethod crud/validate create-type
  [resource]
  (create-validate-subtype resource))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [{:keys [id] :as resource} request]
  (assoc resource :acl {:owners    ["group/nuvla-admin"]
                        :view-meta ["group/nuvla-user"]
                        :edit-acl  [id]}))

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
  [{:status 400, :message "missing or invalid user-template reference"} nil])


;; handles any actions that must be taken after the user is added
(defmulti post-user-add dispatch-conversion)

;; default implementation is a no-op
(defmethod post-user-add :default
  [resource request]
  nil)

;;
;; CRUD operations
;;

(def ^:const initial-state "NEW")

(def user-attrs-defaults
  {:state   initial-state
   :deleted false})

(defn merge-with-defaults
  [resource]
  (merge user-attrs-defaults resource))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


;; requires a user-template to create new User
(defmethod crud/add resource-type
  [{{:keys [template] :as body} :body :as request}]

  (try

    (let [authn-info (auth/current-authentication request)
          desc-attrs (u/select-desc-keys body)
          [frag user] (-> body
                          (assoc :resource-type create-type)
                          (update-in [:template] dissoc :method :id) ;; forces use of template reference
                          (std-crud/resolve-hrefs authn-info true)
                          (update-in [:template] merge desc-attrs) ;; validate desc attrs
                          (crud/validate)
                          (:template)
                          (merge-with-defaults)
                          (tpl->user request))]

      (if frag
        frag
        (if user
          (let [{{:keys [status resource-id]} :body :as result} (add-impl (assoc request :body (merge user desc-attrs)))]
            (when (and resource-id (= 201 status))
              (post-user-add (assoc user :id resource-id, :redirect-url (:redirect-url template)) request))
            result))))

    (catch Exception e
      (or (ex-data e)
          (throw e)))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))

(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(defn delete-children
  [children-resource-types {:keys [params] :as request}]
  (doseq [children-resource-type children-resource-types]
    (try
      (let [filter  (format "%s='%s/%s'" "parent" resource-type (:uuid params))
            entries (second (crud/query-as-admin children-resource-type
                                                 {:cimi-params {:filter (parser/parse-cimi-filter filter)}}))]
        (doseq [{:keys [id]} entries]
          (crud/delete {:params      {:uuid          (some-> id (str/split #"/") second)
                                      :resource-name children-resource-type}
                        :nuvla/authn auth/internal-identity})))
      (catch Exception e
        (log/warn (ex-data e))))))


(def delete-impl (std-crud/delete-fn resource-type))

(defmethod crud/delete resource-type
  [request]
  (delete-children
    [email/resource-type
     credential/resource-type
     user-identifier/resource-type] request)
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))

(defmethod crud/query resource-type
  [request]
  (query-impl request))


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
                      (a/throw-cannot-edit request))
          merged  (->> (dissoc body :name)
                       (merge current))]
      (-> merged
          (dissoc :href)
          (u/update-timestamps)
          (crud/validate)
          (db/edit request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


;;
;; initialization: common schema for all user creation methods
;;

(defn create-super-user
  [password]
  ;; FIXME: nasty hack to ensure username-password user-template, user-identifier and group index are available
  (group/initialize)
  (username-password/initialize)
  (user-identifier/initialize)
  (if (nil? (password/identifier->user-id "super"))
    (do
      (log/info "user 'super' does not exist; attempting to create it")
      (std-crud/add-if-absent (str resource-type " 'super'") resource-type
                              {:template
                               {:href     (str p/resource-type "/" username-password/registration-method)
                                :username "super"
                                :password password}})
      (if-let [super-user-id (password/identifier->user-id "super")]
        (do (log/info "created user 'super' with identifier" super-user-id)
            (let [request {:params      {:resource-name group/resource-type
                                         :uuid          "nuvla-admin"}
                           :body        {:users [super-user-id]}
                           :nuvla/authn auth/internal-identity}
                  {:keys [status]} (crud/edit request)]
              (when (not= status 200)
                (log/error "could not append super in nuvla-admin group!"))))
        (log/error "could not create user 'super'")))
    (do
      (log/info "user 'super' already exists; skip trying to create it"))))


(defn initialize
  []
  (std-crud/initialize resource-type ::user/schema)
  (when-let [super-password (env/env :nuvla-super-password)]
    (create-super-user super-password)))
