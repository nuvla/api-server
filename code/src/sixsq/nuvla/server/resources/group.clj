(ns sixsq.nuvla.server.resources.group
  "
This resource represents a group of users. The unique identifier for the group
is a kebab-case string, provided when the group is created. All group names
that start with 'nuvla-' are reserved for the server.
"
  (:require
    [clojure.spec.alpha :as s]
    [ring.util.codec :as codec]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.password :as auth-password]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.callback-join-group :as callback-join-group]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.core :as spec-core]
    [sixsq.nuvla.server.resources.spec.group :as group]
    [sixsq.nuvla.server.resources.spec.group-template :as group-tpl]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.resources.customer.utils :as utils]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-admin"
                                   "group/nuvla-user"]
                     :bulk-delete ["group/nuvla-admin"]})


;;
;; validate functions
;;

(def validate-fn (u/create-spec-validation-fn ::group/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::group-tpl/schema-create))


(defmethod crud/validate create-type
  [resource]
  (create-validate-fn resource))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; "Implementations" of multimethod declared in crud namespace
;;

(defn tpl->group
  [{:keys [group-identifier]
    :as   resource}]
  (let [id (str resource-type "/" group-identifier)]
    (-> resource
        (dissoc :group-identifier)
        (assoc :id id
               :users []))))


;; modified to retain id and not call new-identifier
(defn add-impl [{:keys [body]
                 :as   request}]
  (a/throw-cannot-add collection-acl request)
  (let [id (:id body)]
    (db/add
      resource-type
      (-> body
          u/strip-service-attrs
          (assoc :id id
                 :resource-type resource-type)
          u/update-timestamps
          (u/set-created-by request)
          (crud/add-acl request)
          crud/validate)
      {})))


(defmethod crud/add resource-type
  [{:keys [body]
    :as   request}]
  (a/throw-cannot-add collection-acl request)
  (let [authn-info (auth/current-authentication request)
        desc-attrs (u/select-desc-keys body)
        body       (-> body
                       (assoc :resource-type create-type)
                       (std-crud/resolve-hrefs authn-info)
                       (update-in [:template] merge desc-attrs) ;; validate desc attrs
                       (crud/validate)
                       :template
                       tpl->group)]
    (add-impl (assoc request :body body))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (let [id    (str resource-type "/" (-> request :params :uuid))
        users (get-in request [:body :users] [])
        acl   (get-in request [:body :acl] (:acl (crud/retrieve-by-id-as-admin id)))]
    (-> request
        (assoc-in [:body :acl] acl)
        (update-in [:body :acl :view-meta] (comp vec set concat) (conj users id))
        (edit-impl))))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


;;
;; "Implementations" of actions
;;


(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [invite-op      (u/action-map id :invite)
        can-manage?    (a/can-manage? resource request)
        can-edit-data? (a/can-edit-data? resource request)]
    (cond-> (crud/set-standard-operations resource request)
            (and can-manage? can-edit-data?) (update :operations conj invite-op))))


(defn throw-is-already-in-group
  [{:keys [id users] :as resource} user-id]
  (if-not ((set users) user-id)
    resource
    (throw (r/ex-response "user already in group" 400 id))))


(defn throw-cannot-manage
  [{:keys [id] :as resource} request action]
  (if (a/can-manage? resource request)
    resource
    (throw (r/ex-response (format "action not available for %s! %s" action id) 409 id))))


(defmethod crud/do-action [resource-type "invite"]
  [{base-uri :base-uri {username         :username
                        redirect-url     :redirect-url
                        set-password-url :set-password-url} :body {uuid :uuid} :params :as request}]
  (try
    (let [id           (str resource-type "/" uuid)
          user-id      (auth-password/identifier->user-id username)
          _group       (-> (crud/retrieve-by-id-as-admin id)
                           (throw-cannot-manage request "invite")
                           (throw-is-already-in-group user-id))
          invited-by   (auth-password/invited-by request)
          email        (if-let [email-address (some-> user-id auth-password/user-id->email)]
                         email-address
                         (if (s/valid? ::spec-core/email username)
                           username
                           (throw (r/ex-response (str "invalid email '" username "'") 400))))
          callback-url (callback-join-group/create-callback
                         base-uri id
                         :data (cond-> {:email email}
                                       user-id (assoc :user-id user-id)
                                       redirect-url (assoc :redirect-url redirect-url)
                                       set-password-url (assoc :set-password-url set-password-url))
                         :expires (u/ttl->timestamp 2592000)) ;; expire after one month
          invite-url   (if (and (nil? user-id) set-password-url)
                         (str set-password-url "?callback=" (codec/url-encode callback-url)
                              "&type=" (codec/url-encode "invitation")
                              "&username=" (codec/url-encode email))
                         callback-url)]
      (email-utils/send-join-group-email id invited-by invite-url email)
      (r/map-response (format "successfully invited to %s" id) 200 id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; collection
;;

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


(def bulk-delete-impl (std-crud/bulk-delete-fn resource-type collection-acl collection-type))


(defmethod crud/bulk-delete resource-type
  [request]
  (bulk-delete-impl request))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::group/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::group/schema)
  (md/register resource-metadata)

  (let [default-acl {:owners    ["group/nuvla-admin"]
                     :view-meta ["group/nuvla-user"]}]
    (std-crud/add-if-absent (str resource-type "/nuvla-admin") resource-type
                            {:name        "Nuvla Administrator Group"
                             :description "group of users with server administration rights"
                             :template    {:group-identifier "nuvla-admin"
                                           :acl              default-acl}})
    (std-crud/add-if-absent (str resource-type "/nuvla-user") resource-type
                            {:name        "Nuvla Authenticated Users"
                             :description "pseudo-group of users that have been authenticated"
                             :template    {:group-identifier "nuvla-user"
                                           :acl              default-acl}})
    (std-crud/add-if-absent (str resource-type "/nuvla-anon") resource-type
                            {:name        "Nuvla Anonymous Users"
                             :description "pseudo-group of all users authenticated or not"
                             :template    {:group-identifier "nuvla-anon"
                                           :acl              default-acl}})
    (std-crud/add-if-absent (str resource-type "/nuvla-nuvlabox") resource-type
                            {:name        "Nuvla NuvlaBox Systems"
                             :description "pseudo-group of all NuvlaBox systems"
                             :template    {:group-identifier "nuvla-nuvlabox"
                                           :acl              default-acl}})))
