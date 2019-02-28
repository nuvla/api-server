(ns sixsq.nuvla.server.resources.spec.credential-template-service-exoscale
    (:require
      [clojure.spec.alpha :as s]
      [sixsq.nuvla.server.resources.spec.acl :as cimi-acl]
      [sixsq.nuvla.server.resources.spec.common :as cimi-common]
      [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
      [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service :as ct-infra-service]
      [sixsq.nuvla.server.resources.spec.credential-template :as ct]
      [sixsq.nuvla.server.util.spec :as su]
      [spec-tools.core :as st]))


(s/def ::exoscale-api-key
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "exoscale-api-key"
             :json-schema/name "exoscale-api-key"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "exoscale-api-key"
             :json-schema/description "API key for Exoscale"
             :json-schema/help "API key for Exoscale"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::exoscale-api-secret-key
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "exoscale-api-secret-key"
             :json-schema/name "exoscale-api-secret-key"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "exoscale-api-secret-key"
             :json-schema/description "API secret key for Exoscale"
             :json-schema/help "API secret key for Exoscale"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive true)))


(def credential-template-keys-spec
  {:req-un [::exoscale-api-key
            ::exoscale-api-secret-key]})

(def credential-template-create-keys-spec
  {:req-un [::exoscale-api-key
            ::exoscale-api-secret-key]})

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
