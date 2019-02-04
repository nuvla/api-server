(ns com.sixsq.slipstream.ssclj.resources.spec.configuration-template-session-mitreid
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common-namespaces :as common-ns]
    [com.sixsq.slipstream.ssclj.resources.spec.configuration-template :as ps]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::clientID
  (-> (st/spec ::cimi-core/token)
      (assoc :name "clientID"
             :json-schema/name "clientID"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "client ID"
             :json-schema/description "MITREid client ID"
             :json-schema/help "MITREid client ID"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::clientSecret
  (-> (st/spec ::cimi-core/token)
      (assoc :name "clientSecret"
             :json-schema/name "clientSecret"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "client secret"
             :json-schema/description "MITREid client secret"
             :json-schema/help "MITREid client secret associated with registered application"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::authorizeURL
  (-> (st/spec ::cimi-core/token)
      (assoc :name "authorizeURL"
             :json-schema/name "authorizeURL"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "authorization URL"
             :json-schema/description "URL for the authorization phase of the OIDC protocol"
             :json-schema/help "URL for the authorization phase of the OIDC protocol"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::tokenURL
  (-> (st/spec ::cimi-core/token)
      (assoc :name "tokenURL"
             :json-schema/name "tokenURL"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "token URL"
             :json-schema/description "URL for the obtaining a token in the OIDC protocol"
             :json-schema/help "URL for the obtaining a token in the OIDC protocol"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::userProfileURL
  (-> (st/spec ::cimi-core/token)
      (assoc :name "userProfileURL"
             :json-schema/name "userProfileURL"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "user profile URL"
             :json-schema/description "URL for user profile attributes"
             :json-schema/help "URL for user profile attributes"
             :json-schema/group "body"
             :json-schema/order 24
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::publicKey
  (-> (st/spec ::cimi-core/nonblank-string) ;; allows jwk JSON representation
      (assoc :name "publicKey"
             :json-schema/name "publicKey"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "public key"
             :json-schema/description "public key of the server"
             :json-schema/help "public key of the server in PEM or JWK JSON format"
             :json-schema/group "body"
             :json-schema/order 25
             :json-schema/hidden false
             :json-schema/sensitive true)))


(def configuration-template-keys-spec-req
  {:req-un [::ps/instance ::clientID  ::clientSecret ::publicKey ::authorizeURL ::tokenURL ::userProfileURL]})

(def configuration-template-keys-spec-create
  {:req-un [::ps/instance ::clientID ::clientSecret ::publicKey ::authorizeURL ::tokenURL ::userProfileURL]})

;; Defines the contents of the Mi authentication ConfigurationTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     configuration-template-keys-spec-req))

;; Defines the contents of the MitreId authentication template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :configurationTemplate here.
(s/def ::configurationTemplate
  (su/only-keys-maps ps/template-keys-spec
                     configuration-template-keys-spec-create))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::configurationTemplate]}))
