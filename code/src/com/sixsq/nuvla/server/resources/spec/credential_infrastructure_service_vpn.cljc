(ns com.sixsq.nuvla.server.resources.spec.credential-infrastructure-service-vpn
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.credential-infrastructure-service :as cred-infra-service]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::vpn-common-name
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "common-name"
             :json-schema/display-name "common name"
             :json-schema/description "client common name")))


(s/def ::vpn-intermediate-ca
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 1 :kind vector?))
      (assoc :name "intermediate-ca"
             :json-schema/type "array"
             :json-schema/description "vpn intermediate ca certificates"
             :json-schema/indexed false)))

(s/def ::vpn-certificate
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "certificate"
             :json-schema/description "client certificate"
             :json-schema/indexed false)))

(s/def ::vpn-certificate-owner
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "vpn-certificate-owner"
             :json-schema/display-name "vpn certificate owner"
             :json-schema/description "vpn certificate owner ID")))

(def credential-keys-spec {:req-un [::vpn-certificate
                                    ::vpn-common-name
                                    ::vpn-certificate-owner]
                           :opt-un [::vpn-intermediate-ca]})

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     cred-infra-service/credential-service-keys-spec
                     credential-keys-spec))
