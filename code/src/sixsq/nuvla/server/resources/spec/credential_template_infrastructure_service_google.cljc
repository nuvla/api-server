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
             :json-schema/name "project_id"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "project_id"
             :json-schema/description "Google Compute project ID to use"
             :json-schema/help "Project ID associated with the GCE service account"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::private-key-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "private_key_id"
             :json-schema/name "private_key_id"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "private_key_id"
             :json-schema/description "ID of the private key"
             :json-schema/help "ID of the private key"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::private-key
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "private_key"
             :json-schema/name "private_key"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "private_key"
             :json-schema/description "Private key content"
             :json-schema/help "Private key content in a single line"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::client-email
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "client_email"
             :json-schema/name "client_email"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "client_email"
             :json-schema/description "Client email associated with the service account"
             :json-schema/help "Client email associated with the service account"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::client-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "client_id"
             :json-schema/name "client_id"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "client_id"
             :json-schema/description "Client ID associated with the service account"
             :json-schema/help "Client ID associated with the service account"
             :json-schema/group "body"
             :json-schema/order 24
             :json-schema/hidden false
             :json-schema/sensitive false)))


;(s/def ::auth-uri
;       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
;           (assoc :name "auth_uri"
;                  :json-schema/name "auth_uri"
;                  :json-schema/type "string"
;                  :json-schema/provider-mandatory false
;                  :json-schema/consumer-mandatory false
;                  :json-schema/mutable true
;                  :json-schema/consumer-writable true
;
;                  :json-schema/display-name "auth_uri"
;                  :json-schema/description "Authentication URI"
;                  :json-schema/help "Authentication URI"
;                  :json-schema/group "body"
;                  :json-schema/order 25
;                  :json-schema/hidden false
;                  :json-schema/sensitive false)))

;
;(s/def ::token-uri
;       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
;           (assoc :name "token_uri"
;                  :json-schema/name "token_uri"
;                  :json-schema/type "string"
;                  :json-schema/provider-mandatory false
;                  :json-schema/consumer-mandatory false
;                  :json-schema/mutable true
;                  :json-schema/consumer-writable true
;
;                  :json-schema/display-name "token_uri"
;                  :json-schema/description "Token URI"
;                  :json-schema/help "Token URI"
;                  :json-schema/group "body"
;                  :json-schema/order 26
;                  :json-schema/hidden false
;                  :json-schema/sensitive false)))

;
;(s/def ::auth-provider-x509-cert-url
;       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
;           (assoc :name "auth_provider_x509_cert_url"
;                  :json-schema/name "auth_provider_x509_cert_url"
;                  :json-schema/type "string"
;                  :json-schema/provider-mandatory false
;                  :json-schema/consumer-mandatory false
;                  :json-schema/mutable true
;                  :json-schema/consumer-writable true
;
;                  :json-schema/display-name "auth_provider_x509_cert_url"
;                  :json-schema/description "Provider X509 certificates URL"
;                  :json-schema/help "Provider X509 certificates URL"
;                  :json-schema/group "body"
;                  :json-schema/order 27
;                  :json-schema/hidden false
;                  :json-schema/sensitive false)))
;
;
;(s/def ::client-x509-cert-url
;       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
;           (assoc :name "client_x509_cert_url"
;                  :json-schema/name "client_x509_cert_url"
;                  :json-schema/type "string"
;                  :json-schema/provider-mandatory false
;                  :json-schema/consumer-mandatory false
;                  :json-schema/mutable true
;                  :json-schema/consumer-writable true
;
;                  :json-schema/display-name "client_x509_cert_url"
;                  :json-schema/description "Client X509 certificates URL"
;                  :json-schema/help "Client X509 certificates URL"
;                  :json-schema/group "body"
;                  :json-schema/order 27
;                  :json-schema/hidden false
;                  :json-schema/sensitive false)))



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
