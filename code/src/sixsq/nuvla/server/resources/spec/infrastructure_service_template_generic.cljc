(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic
  "
This generic template for service resources simply requires the service type
and endpoint attributes to be specified. The accessible attribute will default
to true, if not provided. This template is most appropriate for pre-existing
services that are managed separately.
  "
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.infrastructure-service :as infrastructure-service]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def service-template-keys-spec
  {:req-un [::infrastructure-service/endpoint]
   :opt-un [::infrastructure-service/state
            ::infrastructure-service/swarm-enabled]})


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
