(ns com.sixsq.nuvla.server.resources.user
  "
User resources contain personal information and a few parameters concerning
the registered users. This is a templated resource, so creating a new user
requires a template. All the SCRUD actions follow the standard CIMI patterns.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.password :as password]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.callback-2fa-activation
     :as callback-2fa-activation]
    [com.sixsq.nuvla.server.resources.callback-2fa-deactivation :as
     callback-2fa-deactivation]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.email :as email]
    [com.sixsq.nuvla.server.resources.group :as group]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.user :as user]
    [com.sixsq.nuvla.server.resources.spec.user-2fa :as user-2fa]
    [com.sixsq.nuvla.server.resources.two-factor-auth.utils :as auth-2fa]
    [com.sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [com.sixsq.nuvla.server.resources.user-interface :as user-interface]
    [com.sixsq.nuvla.server.resources.user-template :as p]
    [com.sixsq.nuvla.server.resources.user-template-username-password :as username-password]
    [com.sixsq.nuvla.server.resources.user-username-password]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]
    [environ.core :as env]))


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
  (assoc resource :acl {:owners   ["group/nuvla-admin"]
                        :edit-acl [id]}))


;;
;; CRUD operations
;;

(def ^:const initial-state "NEW")

(def user-attrs-defaults
  {:state           initial-state
   :deleted         false
   :auth-method-2fa auth-2fa/method-none})

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
          redirect-url (-> body
                           (get-in [:template :redirect-url])
                           (config-nuvla/throw-is-not-authorised-redirect-url))
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


(defn edit-impl
  "Returns edited document or exception data in case of an error."
  [{body :body {uuid :uuid} :params :as request}]
  (try
    (let [id         (str resource-type "/" uuid)
          authn-info (auth/current-authentication request)
          is-user?   (not (a/is-admin? authn-info))]
      (-> (crud/retrieve-by-id-as-admin id)
          (a/throw-cannot-edit request)
          (merge (cond-> body
                         is-user? (dissoc :name :state :auth-method-2fa
                                          :credential-totp)))
          (dissoc :href)
          (u/update-timestamps)
          (u/set-updated-by request)
          (crud/validate)
          db/edit))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/edit resource-type
  [request]
  (-> request
      (update-in [:cimi-params :select] #(vec (remove #{"auth-method-2fa"} %1)))
      (edit-impl)))


(defn enable-2fa?
  [{:keys [auth-method-2fa] :as _resource}]
  (or (= auth-method-2fa auth-2fa/method-none)
      (nil? auth-method-2fa)))

(defn can-enable-2fa?
  [resource request]
  (and (a/can-manage? resource request)
       (enable-2fa? resource)
       (some? (:credential-password resource))))


(defn can-disable-2fa?
  [resource request]
  (and (a/can-manage? resource request)
       (not (enable-2fa? resource))
       (some? (:credential-password resource))))


(defn set-resource-ops
  [{:keys [id] :as resource} request]
  (let [ops (cond-> []
                    (a/can-edit? resource request) (conj (u/operation-map id :edit))
                    (a/can-delete? resource request) (conj (u/operation-map id :delete))
                    (can-enable-2fa? resource request) (conj (u/action-map id :enable-2fa))
                    (can-disable-2fa? resource request) (conj (u/action-map id :disable-2fa)))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))


(defmethod crud/set-operations resource-type
  [resource request]
  (if (u/is-collection? resource-type)
    (crud/set-standard-collection-operations resource request)
    (set-resource-ops resource request)))


(defn throw-action-2fa-authorized
  [resource request can-2fa?-fn]
  (if (can-2fa?-fn resource request)
    resource
    (throw
      (r/ex-response
        "You can't enable 2FA!" 403))))


(def validate-enable-2fa-body-fn
  (u/create-spec-validation-fn ::user-2fa/enable-2fa-body-schema))


(defn enable-2fa
  [{base-uri                  :base-uri {uuid :uuid} :params
    {:keys [method] :as body} :body :as request}]
  (try
    (let [id   (str resource-type "/" uuid)
          user (crud/retrieve-by-id-as-admin id)]
      (throw-action-2fa-authorized user request can-enable-2fa?)
      (validate-enable-2fa-body-fn body)
      (let [token        (auth-2fa/generate-token method user)
            secret       (auth-2fa/generate-secret method user)
            callback-url (callback-2fa-activation/create-callback
                           base-uri id :data
                           (cond-> {:method method}
                                   token (assoc :token token)
                                   secret (assoc :secret secret))
                           :expires (u/ttl->timestamp 120)
                           :tries-left 3)
            method-2fa   method]
        (auth-2fa/send-token method-2fa user token)
        (cond-> (r/map-response
                  "Authorization code requested" 200 id callback-url)
                secret (assoc-in [:body :secret] secret))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn disable-2fa
  [{base-uri :base-uri {uuid :uuid} :params :as request}]
  (try
    (let [id             (str resource-type "/" uuid)
          user           (crud/retrieve-by-id-as-admin id)
          current-method (:auth-method-2fa user)]
      (throw-action-2fa-authorized user request can-disable-2fa?)
      (let [token        (auth-2fa/generate-token current-method user)
            callback-url (callback-2fa-deactivation/create-callback
                           base-uri id :data
                           (cond-> {:method auth-2fa/method-none}
                                   token (assoc :token token))
                           :expires (u/ttl->timestamp 120)
                           :tries-left 3)
            method-2fa   (:auth-method-2fa user)]
        (auth-2fa/send-token method-2fa user token)
        (r/map-response "Authorization code requested" 200 id callback-url)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "enable-2fa"]
  [request]
  (enable-2fa request))


(defmethod crud/do-action [resource-type "disable-2fa"]
  [request]
  (disable-2fa request))


;;
;; initialization: common schema for all user creation methods
;;

(defn create-super-user
  [password]
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
