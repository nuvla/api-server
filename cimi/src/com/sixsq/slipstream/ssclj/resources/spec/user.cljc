(ns com.sixsq.slipstream.ssclj.resources.spec.user
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as cimi-common]
    [com.sixsq.slipstream.ssclj.resources.spec.common-namespaces :as common-ns]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]
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


(s/def ::firstName
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "firstName"
             :json-schema/name "firstName"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "first name"
             :json-schema/description "your first (given) name"
             :json-schema/help "your first (given) name"
             :json-schema/group "body"
             :json-schema/order 30
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::lastName
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "lastName"
             :json-schema/name "lastName"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "last name"
             :json-schema/description "your last (family) name"
             :json-schema/help "your last (family) name"
             :json-schema/group "body"
             :json-schema/order 31
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::organization
  (-> (st/spec string?)
      (assoc :name "organization"
             :json-schema/name "organization"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "organization"
             :json-schema/description "your organization's name"
             :json-schema/help "your organization's name"
             :json-schema/group "body"
             :json-schema/order 32
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::roles
  (-> (st/spec string?)
      (assoc :name "roles"
             :json-schema/name "roles"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "roles"
             :json-schema/description "list of roles"
             :json-schema/help "space-separated list of roles"
             :json-schema/group "body"
             :json-schema/order 33
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


(s/def ::creation
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "creation"
             :json-schema/name "creation"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "dateTime"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "creation"
             :json-schema/description "creation date of user account"
             :json-schema/help "creation date of user account"
             :json-schema/group "body"
             :json-schema/order 35
             :json-schema/hidden true
             :json-schema/sensitive false)))


(s/def ::lastOnline
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "lastOnline"
             :json-schema/name "lastOnline"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "dateTime"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "last online"
             :json-schema/description "timestamp when user was last online"
             :json-schema/help "timestamp when user was last online"
             :json-schema/group "body"
             :json-schema/order 36
             :json-schema/hidden true
             :json-schema/sensitive false)))


(s/def ::lastExecute
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "lastExecute"
             :json-schema/name "lastExecute"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "dateTime"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "last execution"
             :json-schema/description "timestamp when user last executed a workflow"
             :json-schema/help "timestamp when user last executed a workflow"
             :json-schema/group "body"
             :json-schema/order 37
             :json-schema/hidden true
             :json-schema/sensitive false)))


(s/def ::activeSince
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "activeSince"
             :json-schema/name "activeSince"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "dateTime"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "active since"
             :json-schema/description "start of current user session"
             :json-schema/help "start of current user session"
             :json-schema/group "body"
             :json-schema/order 38
             :json-schema/hidden true
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


(s/def ::githublogin
  (-> (st/spec string?)
      (assoc :name "githublogin"
             :json-schema/name "githublogin"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "githublogin"
             :json-schema/description "GitHub login identifier (unused)"
             :json-schema/help "GitHub login identifier (unused)"
             :json-schema/group "body"
             :json-schema/order 41
             :json-schema/hidden true
             :json-schema/sensitive false)))


(s/def ::cyclonelogin
  (-> (st/spec string?)
      (assoc :name "cyclonelogin"
             :json-schema/name "cyclonelogin"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "cyclonelogin"
             :json-schema/description "CYCLONE login identifier (unused)"
             :json-schema/help "CYCLONE login identifier (unused)"
             :json-schema/group "body"
             :json-schema/order 42
             :json-schema/hidden true
             :json-schema/sensitive false)))


(s/def ::identityAttribute
  (-> (st/spec (s/and string? #(re-matches #"^[a-z0-9]+(-[a-z0-9]+)*:.+$" %)))
      (assoc :name "identityAttribute"
             :json-schema/name "identityAttribute"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "identityAttribute"
             :json-schema/description "external identity attribute (unused)"
             :json-schema/help "external identity attribute (unused)"
             :json-schema/group "body"
             :json-schema/order 43
             :json-schema/hidden true
             :json-schema/sensitive false)))


(s/def ::externalIdentity
  (-> (st/spec (s/nilable (s/coll-of ::identityAttribute :min-count 1)))
      (assoc :name "externalIdentity"
             :json-schema/name "externalIdentity"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "Array"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "externalIdentity"
             :json-schema/description "list of external identity attributes (unused)"
             :json-schema/help "list of external identity attributes (unused)"
             :json-schema/group "body"
             :json-schema/order 44
             :json-schema/hidden true
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
            ::cimi-common/resourceURI
            ::cimi-common/created
            ::cimi-common/updated
            ::cimi-common/acl]
   :opt-un [::cimi-common/name
            ::cimi-common/description
            ::cimi-common/properties
            ::cimi-common/parent
            ::cimi-common/resourceMetadata
            ::cimi-common/operations]})


(def user-keys-spec
  {:req-un [::username
            ::emailAddress]
   :opt-un [::firstName
            ::lastName
            ::organization
            ::method
            ::href
            ::password
            ::roles
            ::isSuperUser
            ::state
            ::deleted
            ::creation
            ::lastOnline
            ::lastExecute
            ::activeSince
            ::githublogin
            ::cyclonelogin
            ::externalIdentity]})


(s/def ::schema
  (su/only-keys-maps user-common-attrs
                     user-keys-spec))
