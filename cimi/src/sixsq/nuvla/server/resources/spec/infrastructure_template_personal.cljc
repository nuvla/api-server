(ns sixsq.nuvla.server.resources.spec.infrastructure-template-personal
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.infrastructure-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::endpoint
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "endpoint"
             :json-schema/name "endpoint"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "endpoint"
             :json-schema/description "public API endpoint for the infrastructure"
             :json-schema/help "the public endpoint where the infrastructure API is running"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::tls-ca
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "tls-ca"
             :json-schema/name "tls-ca"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "tls-ca"
             :json-schema/description "CA certificate for the API endpoint"
             :json-schema/help "CA certificate to be used for the TLS verification"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::tls-cert
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "tls-cert"
             :json-schema/name "tls-cert"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "tls-cert"
             :json-schema/description "client certificate for the API endpoint"
             :json-schema/help "client certificate to be used for the TLS verification"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::tls-key
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "tls-key"
             :json-schema/name "tls-key"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "tls-key"
             :json-schema/description "key for the API endpoint"
             :json-schema/help "key to be used for the TLS verification"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive true)))


(def infrastructure-template-keys-spec
  {:req-un [::endpoint]
   :opt-un [::tls-ca
            ::tls-cert
            ::tls-key]})


;; Defines the contents of the internal SessionTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     infrastructure-template-keys-spec))


;; Defines the contents of the internal template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     infrastructure-template-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
