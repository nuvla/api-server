(ns sixsq.nuvla.server.resources.spec.configuration-template-openvpn-api
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::endpoint
  (-> (st/spec ::core/url)
      (assoc :name "endpoint"
             :json-schema/description "Endpoint api to use to generate openvpn credentials"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive true)))


(def infra-srvc-id-regex #"^infrastructure-service/[a-zA-Z0-9-]+$")

(s/def ::infrastructure-service-id
  (-> (st/spec (s/and string? #(re-matches infra-srvc-id-regex %)))
      (assoc :name "infrastructure-service-id"
             :json-schema/type "string"
             :json-schema/display-name "Infrastructure service ID"
             :json-schema/description "Infrastructure service ID")))


(s/def ::infrastructure-services
  (-> (st/spec (s/coll-of ::infrastructure-service-id :kind vector? :distinct true))
      (assoc :name "infrastructure-services"
             :json-schema/type "array"
             :json-schema/description "list of infrastructure services using following resource"

             :json-schema/order 21)))


(s/def ::instance (st/spec ::ps/instance))


(def configuration-template-keys-spec-req
  {:req-un [::instance
            ::endpoint
            ::infrastructure-services]})

(def configuration-template-keys-spec-create
  {:req-un [::instance
            ::endpoint
            ::infrastructure-services]})

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
