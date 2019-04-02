(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service-swarm
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service :as cred-infra-service]
    [sixsq.nuvla.server.resources.spec.credential-template]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::ca
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "ca"
             :json-schema/name "ca"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable false
             :json-schema/consumer-writable true

             :json-schema/display-name "CA"
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
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable false
             :json-schema/consumer-writable true

             :json-schema/display-name "cert"
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
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable false
             :json-schema/consumer-writable true

             :json-schema/display-name "key"
             :json-schema/description "client's private certificate"
             :json-schema/help "client's private certificate"
             :json-schema/group "body"
             :json-schema/order 42
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     cred-infra-service/credential-service-keys-spec
                     {:req-un [::ca
                               ::cert
                               ::key]}))
