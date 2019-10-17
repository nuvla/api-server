(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service-openvpn
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service :as cred-infra-service]
    [sixsq.nuvla.server.resources.spec.credential-template]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::openvpn-common-name
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "common-name"
             :json-schema/display-name "common name"
             :json-schema/description "client common name")))


(s/def ::openvpn-intermediate-ca
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 1 :kind vector?))
      (assoc :name "intermediate-ca"
             :json-schema/type "array"
             :json-schema/description "openvpn intermediate ca certificates"
             :json-schema/indexed false)))


(s/def ::openvpn-certificate
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "certificate"
             :json-schema/description "client certificate"
             :json-schema/indexed false)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     cred-infra-service/credential-service-keys-spec
                     {:req-un [::openvpn-certificate
                               ::openvpn-common-name]
                      :opt-un [::openvpn-intermediate-ca]}))
