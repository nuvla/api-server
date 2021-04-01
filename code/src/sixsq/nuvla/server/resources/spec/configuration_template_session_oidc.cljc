(ns sixsq.nuvla.server.resources.spec.configuration-template-session-oidc
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
             :json-schema/description "OIDC client secret"
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


(s/def ::public-key
  (-> (st/spec ::cimi-core/nonblank-string)                 ;; allows jwk JSON representation
      (assoc :name "public-key"
             :json-schema/displayName "public key"
             :json-schema/description "public key of the server in PEM or JWK JSON format"
             :json-schema/group "body"
             :json-schema/order 25
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::redirect-url-resource
  (-> (st/spec #{"hook", "callback"})
      (assoc :name "redirect url resource"
             :json-schema/type "string"
             :json-schema/displayName "redirect url resource"
             :json-schema/description "redirect url resource"
             :json-schema/group "body"
             :json-schema/order 25
             :json-schema/hidden false
             :json-schema/value-scope {:values  ["hook", "callback"]
                                       :default "callback"})))


(def configuration-template-keys-spec-req
  {:req-un [::ps/instance ::client-id ::public-key
            ::authorize-url ::token-url ::redirect-url-resource]
   :opt-un [::client-secret ::redirect-url-resource]})

(def configuration-template-keys-spec-create
  {:req-un [::ps/instance ::client-id ::public-key
            ::authorize-url ::token-url ::redirect-url-resource]
   :opt-un [::client-secret]})

;; Defines the contents of the OIDC authentication configuration-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     configuration-template-keys-spec-req))

;; Defines the contents of the OIDC authentication template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     configuration-template-keys-spec-create))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
