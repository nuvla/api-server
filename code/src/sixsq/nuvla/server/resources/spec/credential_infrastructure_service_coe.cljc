(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service-coe
  "
Spec for credentials of Container Orchestration Engine (COE).
"
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service :as cred-infra-service]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::ca
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "ca"
             :json-schema/display-name "CA"
             :json-schema/description "public certificate of the Certificate Authority (CA)"

             :json-schema/order 40
             :json-schema/editable false)))


(s/def ::cert
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "cert"
             :json-schema/description "client's public certificate"

             :json-schema/order 41
             :json-schema/editable false)))


(s/def ::key
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "key"
             :json-schema/description "client's private certificate"

             :json-schema/order 42
             :json-schema/editable false
             :json-schema/sensitive true)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     cred-infra-service/credential-service-keys-spec
                     {:req-un [::ca
                               ::cert
                               ::key]}))
