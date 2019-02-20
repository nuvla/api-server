(ns sixsq.nuvla.server.resources.spec.infrastructure-nuvla
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.infrastructure :as infra]
    [sixsq.nuvla.server.resources.spec.infrastructure-template :as ps]
    [sixsq.nuvla.server.resources.spec.infrastructure-template-nuvla :as nuvla]
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


;; state of the full infrastructure
(s/def ::state
  (-> (st/spec #{"CREATED",
                 "STARTING", "STARTED",
                 "STOPPING", "STOPPED",
                 "PAUSING", "PAUSED",
                 "SUSPENDING", "SUSPENDED",
                 "ERROR"})
      (assoc :name "state"
             :json-schema/name "state"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "state"
             :json-schema/description "state of infrastructure"
             :json-schema/help "standard CIMI state of infrastructure"
             :json-schema/group "body"
             :json-schema/order 10
             :json-schema/hidden false
             :json-schema/sensitive false

             :json-schema/value-scope {:values  ["CREATED",
                                                 "STARTING", "STARTED",
                                                 "STOPPING", "STOPPED",
                                                 "PAUSING", "PAUSED",
                                                 "SUSPENDING", "SUSPENDED",
                                                 "ERROR"]
                                       :default "CREATED"})))


(def infra-keys-spec
  {:req-un [::state]
   :opt-un [::endpoint
            ::tls-key
            ::tls-cert
            ::tls-ca]})


(s/def ::schema
  (su/only-keys-maps infra/infrastructure-keys-spec
                     infra-keys-spec))


;; multiple methods to create an ssh public key, so multiple schemas
(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::nuvla/template]}))
