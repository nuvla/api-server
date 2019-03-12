(ns sixsq.nuvla.server.resources.spec.credential-template-service-azure
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service :as ct-infra-service]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::azure-client-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "azure-client-id"
             :json-schema/name "azure-client-id"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "azure-client-id"
             :json-schema/description "Azure client ID"
             :json-schema/help "Azure client ID"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::azure-client-secret
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "azure-client-secret"
             :json-schema/name "azure-client-secret"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "azure-client-secret"
             :json-schema/description "Azure client secret"
             :json-schema/help "Azure client secret"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::azure-subscription-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "azure-subscription-id"
             :json-schema/name "azure-subscription-id"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "azure-subscription-id"
             :json-schema/description "Azure subscription ID"
             :json-schema/help "Azure subscription ID"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def credential-template-keys-spec
  {:req-un [::azure-subscription-id
            ::azure-client-secret
            ::azure-client-id]})

(def credential-template-create-keys-spec
  {:req-un [::azure-subscription-id
            ::azure-client-secret
            ::azure-client-id]})

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
