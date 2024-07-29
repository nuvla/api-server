(ns com.sixsq.nuvla.server.resources.spec.infrastructure-service-template-vpn
  "
This template requires the parameters necessary to create a new VPN server.
  "
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.infrastructure-service-template :as infra-service-tpl]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::endpoint
  (-> (st/spec ::core/url)
      (assoc :name "endpoint"
             :json-schema/description "vpn API endpoint")))


(s/def ::protocol
  (-> (st/spec #{"tcp" "udp"})
      (assoc :name "protocol"
             :json-schema/type "string"
             :json-schema/description "protocol"

             :json-schema/value-scope {:values  ["tcp", "udp"]
                                       :default "tcp"})))

(s/def ::port
  (-> (st/spec ::core/port)
      (assoc :name "port"
             :json-schema/display-name "port"
             :json-schema/description "port")))

(s/def ::vpn-endpoint
  (-> (st/spec (su/only-keys :req-un [::port ::protocol ::endpoint]))
      (assoc :name "operation"
             :json-schema/type "map"
             :json-schema/description "operation definition (name, URL) for a resource"

             :json-schema/server-managed true
             :json-schema/editable false)))

(s/def ::vpn-endpoints
  (-> (st/spec (s/coll-of ::vpn-endpoint :kind vector? :min-count 1))
      (assoc :name "vpn-endpoints"
             :json-schema/description "vpn endpoints"
             :json-schema/description "vpn endpoints")))


(s/def ::vpn-scope
  (-> (st/spec #{"customer", "nuvlabox"})
      (assoc :name "vpn-scope"
             :json-schema/type "string"
             :json-schema/description "vpn scope to be used with"
             :json-schema/value-scope {:values ["customer", "nuvlabox"]})))


(s/def ::vpn-shared-key
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "vpn-shared-key"
             :json-schema/display-name "vpn shared key"
             :json-schema/description "vpn shared key for initial packets encryption")))


(s/def ::vpn-ca-certificate
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "vpn-ca-certificate"
             :json-schema/display-name "vpn ca certificate"
             :json-schema/description "vpn ca certificate"
             :json-schema/indexed false)))


(s/def ::vpn-intermediate-ca
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 1 :kind vector?))
      (assoc :name "intermediate-ca"
             :json-schema/type "array"
             :json-schema/description "vpn intermediate ca certificates"
             :json-schema/indexed false)))


(s/def ::vpn-common-name-prefix
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "vpn-common-name-prefix"
             :json-schema/display-name "vpn common name prefix"
             :json-schema/description "vpn common name prefix")))


(def service-template-keys-spec
  {:req-un [::vpn-scope
            ::vpn-endpoints
            ::vpn-ca-certificate]
   :opt-un [::vpn-shared-key
            ::vpn-common-name-prefix
            ::vpn-intermediate-ca]})


;; Defines the contents of the this service-template resource.
(s/def ::schema
  (su/only-keys-maps infra-service-tpl/resource-keys-spec
                     service-template-keys-spec))


;; Defines the contents of the template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps infra-service-tpl/template-keys-spec
                                  service-template-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps infra-service-tpl/create-keys-spec
                     {:req-un [::template]}))
