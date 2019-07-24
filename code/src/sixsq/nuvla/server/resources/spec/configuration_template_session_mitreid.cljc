(ns sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::client-id
  (-> (st/spec ::cimi-core/token)
      (assoc :name "client-id"
             :json-schema/displayName "client ID"
             :json-schema/description "MITREid client ID"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::client-secret
  (-> (st/spec ::cimi-core/token)
      (assoc :name "client-secret"
             :json-schema/displayName "client secret"
             :json-schema/description "MITREid client secret associated with registered application"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::authorize-url
  (-> (st/spec ::cimi-core/token)
      (assoc :name "authorize-url"
             :json-schema/displayName "authorization URL"
             :json-schema/description "URL for the authorization phase of the OIDC protocol"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::token-url
  (-> (st/spec ::cimi-core/token)
      (assoc :name "token-url"
             :json-schema/displayName "token URL"
             :json-schema/description "URL for the obtaining a token in the OIDC protocol"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::user-profile-url
  (-> (st/spec ::cimi-core/token)
      (assoc :name "user-profile-url"
             :json-schema/displayName "user profile URL"
             :json-schema/description "URL for user profile attributes"
             :json-schema/group "body"
             :json-schema/order 24
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::public-key
  (-> (st/spec ::cimi-core/nonblank-string) ;; allows jwk JSON representation
      (assoc :name "public-key"
             :json-schema/displayName "public key"
             :json-schema/description "public key of the server in PEM or JWK JSON format"
             :json-schema/group "body"
             :json-schema/order 25
             :json-schema/hidden false
             :json-schema/sensitive true)))


(def configuration-template-keys-spec-req
  {:req-un [::ps/instance ::client-id  ::client-secret ::public-key ::authorize-url ::token-url ::user-profile-url]})

(def configuration-template-keys-spec-create
  {:req-un [::ps/instance ::client-id ::client-secret ::public-key ::authorize-url ::token-url ::user-profile-url]})

;; Defines the contents of the Mi authentication configuration-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     configuration-template-keys-spec-req))

;; Defines the contents of the MitreId authentication template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     configuration-template-keys-spec-create))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
