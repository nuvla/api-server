(ns sixsq.nuvla.server.resources.spec.configuration-template-infrastructure-service-openvpn
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::credential-api-endpoint
  (-> (st/spec ::cimi-core/url)
      (assoc :name "credential-api-endpoint"
             :json-schema/displayName "credential api endpoint"
             :json-schema/description "NuvlaBox OpenVPN api to generate credentials")))


(s/def ::instance (st/spec ::ps/instance))


(def configuration-template-keys-spec-req
  {:req-un [::instance ::credential-api-endpoint]})

(def configuration-template-keys-spec-create
  {:req-un [::instance ::credential-api-endpoint]})

;; Defines the contents of the openvpn configuration-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     configuration-template-keys-spec-req))

;; Defines the contents of the openvpn api template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     configuration-template-keys-spec-create))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
