(ns sixsq.nuvla.server.resources.spec.user-template-email-password
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.user-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::username
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "username"
             :json-schema/name "username"
             :json-schema/type "string"
             :json-schema/required true
             :json-schema/editable true

             :json-schema/display-name "username"
             :json-schema/description "your username"
             :json-schema/help "your username"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))

(s/def ::email
  (-> (st/spec ::cimi-core/email)
      (assoc :name "email"
             :json-schema/name "email"
             :json-schema/type "string"
             :json-schema/required true
             :json-schema/editable true

             :json-schema/display-name "email address"
             :json-schema/description "your email address"
             :json-schema/help "your email address"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::password
  (-> (st/spec string?)
      (assoc :name "password"
             :json-schema/name "password"
             :json-schema/type "string"
             :json-schema/required true
             :json-schema/editable true

             :json-schema/display-name "password"
             :json-schema/description "password for your account"
             :json-schema/help "password for your account"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive true)))


;; no good defaults for these keys, make them optional in template
(def user-template-password-keys-opt
  {:opt-un [::username
            ::email
            ::password]})


;; expanded template must have these keys defined
(def user-template-password-keys-req
  {:req-un [::email
            ::password]
   :opt-un [::username]})


(def user-template-password-keys-href
  {:opt-un [::ps/href]})


;; Defines the contents of the password user-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     user-template-password-keys-opt))


;; Defines the contents of the password template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     user-template-password-keys-req
                     user-template-password-keys-href))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
