(ns sixsq.nuvla.server.resources.spec.infrastructure-service
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::method
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "method"
             :json-schema/display-name "method"
             :json-schema/description "service creation method"

             :json-schema/order 20
             :json-schema/hidden true)))


(s/def ::type
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "type"
             :json-schema/display-name "service type"
             :json-schema/description "kebab-case identifier for the service type"

             :json-schema/order 21)))


(s/def ::endpoint
  (-> (st/spec ::cimi-core/url)
      (assoc :name "endpoint"
             :json-schema/display-name "endpoint"
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
             :json-schema/display-name "state"
             :json-schema/description "state of service"

             :json-schema/order 23

             :json-schema/value-scope {:values  ["CREATED",
                                                 "STARTING", "STARTED",
                                                 "STOPPING", "STOPPED",
                                                 "PAUSING", "PAUSED",
                                                 "SUSPENDING", "SUSPENDED",
                                                 "ERROR"]
                                       :default "CREATED"})))


(s/def ::management-credential-id
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "management-credential-id"
             :json-schema/display-name "management credential id"
             :json-schema/description "id of the credential used to manage this service")))


;;
;; this is meant for COE services only
;; TODO: move this to COE specific schemas
;;
(s/def ::machine-name ::cimi-core/nonblank-string)
(s/def ::machine-config-base64 ::cimi-core/nonblank-string)

(s/def ::node
  (-> (st/spec (su/only-keys :req-un [::machine-name
                                      ::machine-config-base64]))
      (assoc :name "node"
             :json-schema/type "map"
             :json-schema/display-name "node"
             :json-schema/description "node within the swarm cluster"

             :json-schema/editable false
             :json-schema/order 23)))


(s/def ::nodes
  (-> (st/spec (s/coll-of ::node :min-count 1 :kind vector?))
      (assoc :name "nodes"
             :json-schema/type "array"
             :json-schema/display-name "nodes"
             :json-schema/description "List of base64 encoded configurations for each Swarm machine"

             :json-schema/editable false
             :json-schema/order 24
             :json-schema/hidden true)))

;;
;; -------
;;

(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::cimi-common/parent         ;; required for services
                               ::method
                               ::type
                               ::state]
                      :opt-un [::endpoint
                               ::management-credential-id
                               ::nodes]}))
