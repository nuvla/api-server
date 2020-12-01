(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic
  "
This generic template for service resources simply requires the service type
and endpoint attributes to be specified. The accessible attribute will default
to true, if not provided. This template is most appropriate for pre-existing
services that are managed separately.
  "
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::endpoint
  (-> (st/spec ::core/url)
      (assoc :name "endpoint"
             :json-schema/description "public API endpoint for the service"

             :json-schema/order 22)))


(s/def ::state
  (-> (st/spec #{"CREATED",
                 "STARTING", "STARTED",
                 "STOPPING", "STOPPED",
                 "PAUSING", "PAUSED",
                 "SUSPENDING", "SUSPENDED",
                 "TERMINATING", "TERMINATED",
                 "ERROR"})
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/description "state of service"

             :json-schema/order 23

             :json-schema/value-scope {:values  ["CREATED",
                                                 "STARTING", "STARTED",
                                                 "STOPPING", "STOPPED",
                                                 "PAUSING", "PAUSED",
                                                 "SUSPENDING", "SUSPENDED",
                                                 "TERMINATING", "TERMINATED",
                                                 "ERROR"]
                                       :default "CREATED"})))


(s/def ::swarm-enabled
  (-> (st/spec boolean?)
      (assoc :name "swarm-enabled"
             :json-schema/display-name "swarm enabled"
             :json-schema/description "flags if swarm mode is enabled or not"

             :json-schema/order 25
             :json-schema/hidden true)))


(s/def ::online
  (-> (st/spec boolean?)
      (assoc :name "online"
             :json-schema/display-name "online"
             :json-schema/description "flags if the infrastructure in online or not"

             :json-schema/order 26
             :json-schema/hidden true)))

(s/def ::parent
  (-> (st/spec ::common/parent)
      (assoc :json-schema/type "string")))


(def service-service-keys-spec
  {:req-un [::parent
            ::endpoint]
   :opt-un [::state
            ::swarm-enabled
            ::online]})


(def service-template-keys-spec
  {:req-un [::endpoint]
   :opt-un [::state
            ::swarm-enabled
            ::online]})


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
