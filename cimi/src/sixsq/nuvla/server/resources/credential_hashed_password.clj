(ns sixsq.nuvla.server.resources.credential-hashed-password
  "
Hashed value of a password.
"
  (:require
    [buddy.hashers :as hashers]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-hashed-password :as tpl-hashed-pwd]
    [sixsq.nuvla.server.resources.spec.credential-hashed-password :as hashed-pwd-spec]
    [sixsq.nuvla.server.resources.spec.credential-template-hashed-password :as ct-hashed-pwd-spec]
    [sixsq.nuvla.server.util.log :as logu]))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::hashed-pwd-spec/schema))


;;
;; convert template to credential: hash the plain text password.
;;

(defmethod p/tpl->credential tpl-hashed-pwd/credential-type
  [{:keys [type method password password-repeated]} request]
  (if (= password password-repeated)
    (let [hash (hashers/derive password)]
      [nil {:resource-type p/resource-type
            :type          type
            :method        method
            :hash          hash}])
    (logu/log-and-throw-400 (str "mismatched passwords"))))


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

