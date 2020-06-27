(ns sixsq.nuvla.server.resources.spec.infrastructure-service
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::method
  (-> (st/spec ::core/identifier)
      (assoc :name "method"
             :json-schema/description "service creation method"

             :json-schema/order 20
             :json-schema/hidden true)))


(s/def ::subtype
  (-> (st/spec ::core/identifier)
      (assoc :name "subtype"
             :json-schema/display-name "service subtype"
             :json-schema/description "kebab-case identifier for the service subtype"

             :json-schema/order 21)))


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

;;
;; -------
;;

(def infra-service-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::common/parent ;; required for services
                                  ::method
                                  ::subtype
                                  ::state]
                         :opt-un [::endpoint
                                  ::swarm-enabled
                                  ::online]}]))


(s/def ::schema
       (su/only-keys-maps infra-service-keys-spec))
