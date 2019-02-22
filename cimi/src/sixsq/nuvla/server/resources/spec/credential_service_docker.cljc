(ns sixsq.nuvla.server.resources.spec.credential-service-docker
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.credential-service :as cred-service]
    [sixsq.nuvla.server.resources.spec.credential-template]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::ca
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "ca"
             :json-schema/name "ca"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable false
             :json-schema/consumerWritable true

             :json-schema/displayName "CA"
             :json-schema/description "public certificate of the Certificate Authority (CA)"
             :json-schema/help "public certificate of the Certificate Authority (CA)"
             :json-schema/group "body"
             :json-schema/order 40
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::cert
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "cert"
             :json-schema/name "cert"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable false
             :json-schema/consumerWritable true

             :json-schema/displayName "cert"
             :json-schema/description "client's public certificate"
             :json-schema/help "client's public certificate"
             :json-schema/group "body"
             :json-schema/order 41
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::key
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "key"
             :json-schema/name "key"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable false
             :json-schema/consumerWritable true

             :json-schema/displayName "key"
             :json-schema/description "client's private certificate"
             :json-schema/help "client's private certificate"
             :json-schema/group "body"
             :json-schema/order 42
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     cred-service/credential-service-keys-spec
                     {:req-un [::ca
                               ::cert
                               ::key]}))
