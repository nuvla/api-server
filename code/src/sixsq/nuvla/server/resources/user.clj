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
    [sixsq.nuvla.server.resources.credential-template-hashed-password :as cred-tmpl-pass]
    [sixsq.nuvla.server.resources.email :as email]
    [sixsq.nuvla.server.resources.group :as group]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [sixsq.nuvla.server.resources.user-interface :as user-interface]
    [sixsq.nuvla.server.resources.user-template :as p]
    [sixsq.nuvla.server.resources.user-template-username-password :as username-password]
    [sixsq.nuvla.server.resources.user-username-password]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def resource-metadata (gen-md/generate-metadata ::ns ::user/schema))


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


(defmethod crud/validate create-type
  [resource]
  (user-interface/create-validate-subtype resource))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [{:keys [id] :as resource} _request]
  (assoc resource :acl {:owners    ["group/nuvla-admin"]
                        :view-meta ["group/nuvla-user"]
                        :edit-acl  [id]}))


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
  [{:keys [body form-params headers] :as request}]
  (try
    (let [authn-info   (auth/current-authentication request)
          body         (if (u/is-form? headers) (u/convert-form :template form-params) body)
          redirect-url (get-in body [:template :redirect-url])
          desc-attrs   (u/select-desc-keys body)
          [frag user] (-> body
                          (assoc :resource-type create-type)
                          (update-in [:template] dissoc :method :id) ;; forces use of template reference
                          (std-crud/resolve-hrefs authn-info true)
                          (update-in [:template] merge desc-attrs) ;; validate desc attrs
                          (crud/validate)
                          (:template)
                          (merge-with-defaults)
                          (user-interface/tpl->user request))]

      (or frag
          (when user
            (let [{{:keys [status resource-id]} :body :as result} (add-impl (assoc request :body (merge user desc-attrs)))]
              (when (and resource-id (= 201 status))
                (user-interface/post-user-add
                  (assoc user :id resource-id, :redirect-url redirect-url) request))
              result))))

    (catch Exception e
      (or (ex-data e)
          (throw e)))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))

(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(defn delete-children
  [children-resource-types {:keys [params] :as _request}]
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
  (when-not (contains? body :id)
    (logu/log-and-throw-400 "id is not provided in the document.")))


(defn edit-impl
  "Returns edited document or exception data in case of an error."
  [{body :body :as request}]
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
          (u/set-updated-by request)
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
  (credential/initialize)
  (group/initialize)
  (username-password/initialize)
  (user-identifier/initialize)
  (cred-tmpl-pass/initialize)
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
                  {:keys [status] :as resp} (crud/edit request)]
              (when (not= status 200)
                (log/error "could not append super in nuvla-admin group!" resp))))
        (log/error "could not create user 'super'")))
    (log/info "user 'super' already exists; skip trying to create it")))


(defn initialize
  []
  (std-crud/initialize resource-type ::user/schema)
  (md/register resource-metadata)
  (when-let [super-password (env/env :nuvla-super-password)]
    (create-super-user super-password)))
