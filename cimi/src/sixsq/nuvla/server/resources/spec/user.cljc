(ns sixsq.nuvla.server.resources.spec.user
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; Less restrictive than standard ::cimi-common/id to accommodate OIDC, etc.
(s/def ::id (s/and string? #(re-matches #"^user/.*" %)))


(s/def ::username
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "username"
             :json-schema/name "username"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "username"
             :json-schema/description "username for your account"
             :json-schema/help "username for your account"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::emailAddress
  (-> (st/spec ::cimi-core/email)
      (assoc :name "emailAddress"
             :json-schema/name "emailAddress"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "email address"
             :json-schema/description "your email address"
             :json-schema/help "your email address"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::password
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "password"
             :json-schema/name "password"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
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


(s/def ::full-name
  (-> (st/spec string?)
      (assoc :name "full-name"
             :json-schema/name "full-name"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "full name"
             :json-schema/description "your full name"
             :json-schema/help "your full name"
             :json-schema/group "body"
             :json-schema/order 30
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::state
  (-> (st/spec #{"NEW" "ACTIVE" "DELETED" "SUSPENDED"})
      (assoc :name "state"
             :json-schema/name "state"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "state"
             :json-schema/description "state of user's account"
             :json-schema/help "state of user's account"
             :json-schema/group "body"
             :json-schema/order 34
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::isSuperUser
  (-> (st/spec boolean?)
      (assoc :name "isSuperUser"
             :json-schema/name "isSuperUser"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "boolean"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "super user?"
             :json-schema/description "flag to indicate if user is super user"
             :json-schema/help "flag to indicate if user is super use"
             :json-schema/group "body"
             :json-schema/order 39
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::deleted
  (-> (st/spec boolean?)
      (assoc :name "deleted"
             :json-schema/name "deleted"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "boolean"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "deleted"
             :json-schema/description "flag to indicate if user has been deleted"
             :json-schema/help "flag to indicate if user has been deleted"
             :json-schema/group "body"
             :json-schema/order 40
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::method
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "method"
             :json-schema/name "method"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "method"
             :json-schema/description "user creation method"
             :json-schema/help "user creation method"
             :json-schema/group "body"
             :json-schema/order 50
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::href
  (-> (st/spec string?)
      (assoc :name "href"
             :json-schema/name "href"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "href"
             :json-schema/group "body"
             :json-schema/order 51
             :json-schema/hidden false
             :json-schema/sensitive false)))


;;
;; redefined common attributes to allow for less restrictive
;; resource identifier (::id) for user resources
;;

(def ^:const user-common-attrs
  {:req-un [::id
            ::cimi-common/resource-type
            ::cimi-common/created
            ::cimi-common/updated
            ::cimi-common/acl]
   :opt-un [::cimi-common/name
            ::cimi-common/description
            ::cimi-common/tags
            ::cimi-common/parent
            ::cimi-common/resourceMetadata
            ::cimi-common/operations]})


(def user-keys-spec
  {:req-un [::username
            ::emailAddress]
   :opt-un [::full-name
            ::method
            ::href
            ::password
            ::isSuperUser
            ::state
            ::deleted]})


(s/def ::schema
  (su/only-keys-maps user-common-attrs
                     user-keys-spec))
