(ns sixsq.nuvla.server.resources.credential-hashed-password
  "
Hashed value of a password.
"
  (:require
    [buddy.hashers :as hashers]
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-hashed-password :as tpl-hashed-pwd]
    [sixsq.nuvla.server.resources.spec.credential-hashed-password :as hashed-pwd-spec]
    [sixsq.nuvla.server.resources.spec.credential-template-hashed-password :as ct-hashed-pwd-spec]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const admin-opts {:user-name "INTERNAL", :user-roles ["ADMIN"]})


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::hashed-pwd-spec/schema))


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

(defmethod p/tpl->credential tpl-hashed-pwd/credential-type
  [{:keys [type method password password-repeated]} request]
  (if (= password password-repeated)
    (if (acceptable-password? password)
      (let [hash (hashers/derive password)]
        [nil {:resource-type p/resource-type
              :type          type
              :method        method
              :hash          hash}])
      (throw (r/ex-response acceptable-password-msg 400)))
    (throw (r/ex-response "mismatched passwords" 400))))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::hashed-pwd-spec/schema))


(defmethod p/validate-subtype tpl-hashed-pwd/credential-type
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::ct-hashed-pwd-spec/schema-create))


(defmethod p/create-validate-subtype tpl-hashed-pwd/credential-type
  [resource]
  (create-validate-fn resource))


;;
;; multimethod for editing; remove keys user cannot change
;;

(defmethod p/special-edit tpl-hashed-pwd/credential-type
  [resource request]
  (dissoc resource :hash))


;;
;; operations
;;

(defmethod p/set-credential-operations tpl-hashed-pwd/credential-type
  [resource request]
  (try
    (a/can-modify? resource request)
    (let [href (:id resource)
          ^String resource-type (:resource-type resource)
          ops (if (u/is-collection? resource-type)
                [{:rel (:add c/action-uri) :href href}]
                [{:rel (:edit c/action-uri) :href href}
                 {:rel (:delete c/action-uri) :href href}
                 {:rel (:check-password c/action-uri) :href (str href "/check-password")}
                 {:rel (:change-password c/action-uri) :href (str href "/change-password")}])]
      (assoc resource :operations ops))
    (catch Exception e
      (dissoc resource :operations))))


;;
;; actions
;;

(defmethod crud/do-action [p/resource-type "check-password"]
  [{{uuid :uuid} :params :as request}]
  (let [id (str p/resource-type "/" uuid)]
    (when-let [{:keys [hash] :as resource} (crud/retrieve-by-id-as-admin id)]
      (a/can-modify? resource request)
      (let [current-password (get-in request [:body :password])]
        (if (hashers/check current-password hash)
          (r/map-response "valid password" 200)
          (throw (r/ex-response "invalid password" 403)))))))


(defmethod crud/do-action [p/resource-type "change-password"]
  [{{uuid :uuid} :params :as request}]
  (let [id (str p/resource-type "/" uuid)]
    (when-let [{:keys [hash] :as resource} (crud/retrieve-by-id-as-admin id)]
      (a/can-modify? resource request)
      (let [current-password (get-in request [:body :current-password])]
        (if (hashers/check current-password hash)
          (let [{:keys [new-password new-password-repeated]} (:body request)]
            (if (= new-password new-password-repeated)
              (if (acceptable-password? new-password)
                (let [new-hash (hashers/derive new-password)]
                  (db/edit (assoc resource :hash new-hash) admin-opts)
                  (r/map-response "password changed" 200))
                (throw (r/ex-response acceptable-password-msg 400)))
              (throw (r/ex-response "mismatched passwords" 400))))
          (throw (r/ex-response "invalid password" 403)))))))
