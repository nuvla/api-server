(ns sixsq.nuvla.server.resources.spec.user-template-email-password
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.user-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::username
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "username"
             :json-schema/description "your username"

             :json-schema/order 20)))


(s/def ::email
  (-> (st/spec ::core/email)
      (assoc :name "email"
             :json-schema/display-name "email address"
             :json-schema/description "your email address"

             :json-schema/order 21)))


(s/def ::password
  (-> (st/spec string?)
      (assoc :name "password"
             :json-schema/type "string"
             :json-schema/description "password for your account"

             :json-schema/order 22
             :json-schema/sensitive true)))


;; no good defaults for these keys, make them optional in template
(def user-template-email-password-keys-opt
  {:opt-un [::username
            ::email
            ::password]})


;; expanded template must have these keys defined
(def user-template-email-password-keys-req
  {:req-un [::email
            ::password]
   :opt-un [::username]})


(def user-template-email-password-keys-href
  {:opt-un [::ps/href]})


;; Defines the contents of the password user-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     user-template-email-password-keys-opt))


;; Defines the contents of the password template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  user-template-email-password-keys-req
                                  user-template-email-password-keys-href))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
