(ns sixsq.nuvla.server.resources.spec.user-template-self-registration
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.resources.spec.user-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::password
  (-> (st/spec string?)
      (assoc :name "password"
             :json-schema/name "password"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "password"
             :json-schema/description "password for your account"
             :json-schema/help "password for your account"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::passwordRepeat
  (-> (st/spec string?)
      (assoc :name "passwordRepeat"
             :json-schema/name "passwordRepeat"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "repeated password"
             :json-schema/description "repeated password for verification"
             :json-schema/help "repeated password for verification"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive true)))


(def user-template-self-registration-keys
  {:req-un [::user/username
            ::user/emailAddress
            ::password
            ::passwordRepeat]})

(def user-template-self-registration-keys-href
  {:opt-un [::ps/href]})

;; Defines the contents of the self-registration UserTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     user-template-self-registration-keys))

;; Defines the contents of the self-registration template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     user-template-self-registration-keys
                     user-template-self-registration-keys-href))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
