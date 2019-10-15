(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-openvpn
  "
This template requires the parameters necessary to create a new OpenVpn server.
  "
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::endpoint
  (-> (st/spec ::core/url)
      (assoc :name "endpoint"
             :json-schema/description "openvpn API endpoint")))


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

(s/def ::openvpn-endpoint
  (-> (st/spec (su/only-keys :req-un [::port ::protocol ::endpoint]))
      (assoc :name "operation"
             :json-schema/type "map"
             :json-schema/description "operation definition (name, URL) for a resource"

             :json-schema/server-managed true
             :json-schema/editable false)))

(s/def ::openvpn-endpoints
  (-> (st/spec (s/coll-of ::openvpn-endpoint :kind vector? :min-count 1))
      (assoc :name "openvpn-endpoints"
             :json-schema/description "openvpn endpoints"
             :json-schema/description "openvpn endpoints")))


(s/def ::openvpn-scope
  (-> (st/spec #{"customer", "nuvlabox"})
      (assoc :name "openvpn-scope"
             :json-schema/type "string"
             :json-schema/description "openvpn scope to be used with"
             :json-schema/value-scope {:values ["customer", "nuvlabox"]})))


(s/def ::openvpn-shared-key
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "openvpn-shared-key"
             :json-schema/display-name "openvpn shared key"
             :json-schema/description "openvpn shared key for initial packets encryption")))


(s/def ::openvpn-ca-certificate
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "openvpn-ca-certificate"
             :json-schema/display-name "openvpn ca certificate"
             :json-schema/description "openvpn ca certificate"
             :json-schema/indexed false)))


(s/def ::openvpn-common-name-prefix
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "openvpn-common-name-prefix"
             :json-schema/display-name "openvpn common name prefix"
             :json-schema/description "openvpn common name prefix")))


(def service-template-keys-spec
  {:req-un [::openvpn-scope
            ::openvpn-endpoints
            ::openvpn-ca-certificate]
   :opt-un [::openvpn-shared-key
            ::openvpn-common-name-prefix]})


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
