(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-google
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service :as ct-infra-service]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::project-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "project_id"
             :json-schema/type "string"

             :json-schema/description "Google Compute project ID to use"
             :json-schema/order 20)))


(s/def ::private-key-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "private_key_id"
             :json-schema/type "string"

             :json-schema/description "ID of the private key"
             :json-schema/order 21)))


(s/def ::private-key
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "private_key"
             :json-schema/type "string"

             :json-schema/description "Private key content"
             :json-schema/order 22
             :json-schema/sensitive true)))


(s/def ::client-email
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "client_email"
             :json-schema/type "string"

             :json-schema/description "Client email associated with the service account"
             :json-schema/order 23)))


(s/def ::client-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "client_id"
             :json-schema/type "string"

             :json-schema/description "Client ID associated with the service account"
             :json-schema/order 24)))


;(s/def ::auth-uri
;       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
;           (assoc :name "auth_uri"
;                  :json-schema/type "string"
;
;                  :json-schema/description "Authentication URI"
;                  :json-schema/order 25)))

;
;(s/def ::token-uri
;       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
;           (assoc :name "token_uri"
;                  :json-schema/type "string"
;
;                  :json-schema/description "Token URI"
;                  :json-schema/order 26)))

;
;(s/def ::auth-provider-x509-cert-url
;       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
;           (assoc :name "auth_provider_x509_cert_url"
;                  :json-schema/type "string"
;
;                  :json-schema/description "Provider X509 certificates URL"
;                  :json-schema/order 27)))
;
;
;(s/def ::client-x509-cert-url
;       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
;           (assoc :name "client_x509_cert_url"
;                  :json-schema/type "string"
;
;                  :json-schema/description "Client X509 certificates URL"
;                  :json-schema/order 27)))



(def credential-template-keys-spec
  {:req-un [::project-id
            ::private-key-id
            ::private-key
            ::client-email
            ::client-id]})

(def credential-template-create-keys-spec
  {:req-un [::project-id
            ::private-key-id
            ::private-key
            ::client-email
            ::client-id]})

;; Defines the contents of the api-key CredentialTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     ct-infra-service/credential-template-service-keys-spec
                     credential-template-keys-spec))

;; Defines the contents of the api-key template used in a create resource.
(s/def ::template
  (su/only-keys-maps ct/template-keys-spec
                     ct-infra-service/credential-template-service-create-keys-spec
                     credential-template-create-keys-spec))

(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
