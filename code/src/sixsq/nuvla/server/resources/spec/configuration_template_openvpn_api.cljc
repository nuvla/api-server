(ns sixsq.nuvla.server.resources.spec.configuration-template-openvpn-api
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::endpoint
  (-> (st/spec ::cimi-core/url)
      (assoc :name "endpoint"
             :json-schema/description "Endpoint api to use to generate openvpn credentials"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::instance (st/spec ::ps/instance))


(def configuration-template-keys-spec-req
  {:req-un [::instance ::endpoint]})

(def configuration-template-keys-spec-create
  {:req-un [::instance ::endpoint]})

;; Defines the contents of the github authentication configuration-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     configuration-template-keys-spec-req))

;; Defines the contents of the github authentication template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     configuration-template-keys-spec-create))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
