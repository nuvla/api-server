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
             :json-schema/name "method"
             :json-schema/type "string"
             :json-schema/required true
             :json-schema/editable true

             :json-schema/display-name "method"
             :json-schema/description "service creation method"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden true
             :json-schema/sensitive false)))


(s/def ::type
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "type"
             :json-schema/name "type"
             :json-schema/type "string"
             :json-schema/required true
             :json-schema/editable true

             :json-schema/display-name "service type"
             :json-schema/description "kebab-case identifier for the service type"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::endpoint
  (-> (st/spec ::cimi-core/url)
      (assoc :name "endpoint"
             :json-schema/name "endpoint"
             :json-schema/type "string"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "endpoint"
             :json-schema/description "public API endpoint for the service"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::state
  (-> (st/spec #{"CREATED",
                 "STARTING", "STARTED",
                 "STOPPING", "STOPPED",
                 "PAUSING", "PAUSED",
                 "SUSPENDING", "SUSPENDED",
                 "ERROR"})
      (assoc :name "state"
             :json-schema/name "state"
             :json-schema/type "string"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "state"
             :json-schema/description "state of service"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive false

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
             :json-schema/name "management-credential-id"
             :json-schema/type "string"
             :json-schema/required false
             :json-schema/editable true

             :json-schema/display-name "management credential id"
             :json-schema/description "id of the credential used to manage this service"
             :json-schema/hidden false
             :json-schema/sensitive false)))


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
             :json-schema/name "node"
             :json-schema/type "map"
             :json-schema/required false
             :json-schema/editable false

             :json-schema/display-name "node"
             :json-schema/description "node within the swarm cluster"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::nodes
  (-> (st/spec (s/coll-of ::node :kind vector?))
      (assoc :name "nodes"
             :json-schema/name "nodes"
             :json-schema/type "array"
             :json-schema/required false
             :json-schema/editable false

             :json-schema/display-name "nodes"
             :json-schema/description "List of base64 encoded configurations for each Swarm machine"
             :json-schema/group "body"
             :json-schema/order 24
             :json-schema/hidden true
             :json-schema/sensitive false)))

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
