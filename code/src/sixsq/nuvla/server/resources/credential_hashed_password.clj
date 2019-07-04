(ns sixsq.nuvla.server.resources.credential-hashed-password
  "
This credential stores the hashed value of a password. Used typically to store
the passwords for Nuvla user accounts. The resource provides actions to check a
plain text password against the stored hash and to change the password (hash).
"
  (:require
    [buddy.hashers :as hashers]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-hashed-password :as tpl-hashed-pwd]
    [sixsq.nuvla.server.resources.spec.credential-hashed-password :as hashed-pwd-spec]
    [sixsq.nuvla.server.resources.spec.credential-template-hashed-password :as ct-hashed-pwd-spec]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.resources.resource-metadata :as md]))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::hashed-pwd-spec/schema)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::hashed-pwd-spec/schema)))


;;
;; utility to verify that password is acceptable
;;

(def acceptable-password-msg "password must contain at least one uppercase character, one lowercase character, one digit, one special character, and at least 8 characters in total")


(defn acceptable-password?
  [password]
  (and (string? password)
       (>= (count password) 8)
       (re-matches #"^.*[A-Z].*$" password)
       (re-matches #"^.*[a-z].*$" password)
       (re-matches #"^.*[0-9].*$" password)
       (re-matches #"^.*[^A-Za-z0-9].*$" password)))


;;
;; convert template to credential: hash the plain text password.
;;

(defmethod p/tpl->credential tpl-hashed-pwd/credential-subtype
  [{:keys [subtype method password parent]} request]
  (if (acceptable-password? password)
    (let [hash (hashers/derive password)]
      [nil (cond-> {:resource-type p/resource-type
                    :subtype       subtype
                    :method        method
                    :hash          hash}
                   parent (assoc :parent parent))])
    (throw (r/ex-response acceptable-password-msg 400))))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::hashed-pwd-spec/schema))


(defmethod p/validate-subtype tpl-hashed-pwd/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::ct-hashed-pwd-spec/schema-create))


(defmethod p/create-validate-subtype tpl-hashed-pwd/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; multimethod for editing; remove keys user cannot change
;;

(defmethod p/special-edit tpl-hashed-pwd/credential-subtype
  [resource request]
  (dissoc resource :hash))


;;
;; operations
;;

(defn set-collection-ops
  [{:keys [id] :as resource} request]
  (if (a/can-add? resource request)
    (assoc resource :operations [(u/operation-map id :add)])
    (dissoc resource :operations)))


(defn set-resource-ops
  [{:keys [id] :as resource} request]
  (let [can-manage? (a/can-manage? resource request)
        ops         (cond-> []
                            (a/can-edit? resource request) (conj (u/operation-map id :edit))
                            (a/can-delete? resource request) (conj (u/operation-map id :delete))
                            can-manage? (conj (u/action-map id :check-password))
                            can-manage? (conj (u/action-map id :change-password)))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))


(defmethod p/set-credential-operations tpl-hashed-pwd/credential-subtype
  [{:keys [resource-type] :as resource} request]
  (if (u/is-collection? resource-type)
    (set-collection-ops resource request)
    (set-resource-ops resource request)))


;;
;; actions
;;

(defmethod crud/do-action [p/resource-type "check-password"]
  [{{uuid :uuid} :params :as request}]
  (let [id (str p/resource-type "/" uuid)]
    (when-let [{:keys [hash] :as resource} (crud/retrieve-by-id-as-admin id)]
      (a/can-edit? resource request)
      (let [current-password (get-in request [:body :password])]
        (if (hashers/check current-password hash)
          (r/map-response "valid password" 200)
          (throw (r/ex-response "invalid password" 403)))))))


(defmethod crud/do-action [p/resource-type "change-password"]
  [{{uuid :uuid} :params body :body :as request}]
  (let [id (str p/resource-type "/" uuid)]
    (when-let [{:keys [hash] :as resource} (crud/retrieve-by-id-as-admin id)]
      (a/can-edit? resource request)
      (let [{:keys [current-password new-password]} body]
        (if (hashers/check current-password hash)
          (if (acceptable-password? new-password)
            (let [new-hash (hashers/derive new-password)]
              (db/edit (assoc resource :hash new-hash) {:nuvla/authn auth/internal-identity})
              (r/map-response "password changed" 200))
            (throw (r/ex-response acceptable-password-msg 400)))
          (throw (r/ex-response "invalid password" 403)))))))
