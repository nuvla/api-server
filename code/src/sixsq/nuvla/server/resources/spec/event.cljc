(ns sixsq.nuvla.server.resources.spec.event
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.events.config :as events-config]
    [sixsq.nuvla.server.resources.spec.acl-common :as acl-common]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::event-type
  (-> (st/spec string?)
      (assoc :name "event-type"
             :json-schema/type "string"
             :json-schema/description "type of event")))


(s/def ::category
  (-> (st/spec #{"command" "crud" "action" "state" "alarm" "email"})
      (assoc :name "category"
             :json-schema/type "string"
             :json-schema/description "category of event"
             :json-schema/value-scope {:values ["alarm" "crud" "action" "system" "email"]}

             :json-schema/order 30)))


(s/def ::subcategory
  (-> (st/spec string?)
      (assoc :name "subcategory"
             :json-schema/type "string"
             :json-schema/description "subcategory of event")))


(s/def ::severity
  (-> (st/spec #{"critical" "high" "medium" "low"})
      (assoc :name "severity"
             :json-schema/type "string"
             :json-schema/description "severity level of event"
             :json-schema/value-scope {:values ["critical", "high", "medium", "low"]}

             :json-schema/order 31)))


(s/def ::timestamp
  (-> (st/spec ::core/timestamp)
      (assoc :name "timestamp"
             :json-schema/description "time of the event"

             :json-schema/order 32)))


(s/def ::message
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "message"
             :json-schema/description "event message")))


;; Essential details of the events, indexed by ES.
;; DO NOT overuse, ES has a max number of keys it can index.
;; TODO: make it stricter, depending on the event category
(s/def ::details
  (-> (st/spec (su/constrained-map keyword? any?))
      (assoc :name "details"
             :json-schema/type "map"
             :json-schema/description "event details")))


;; Freeform payload, not indexed by ES. Can be used to store
;; full request/response payloads, for replaying.
(s/def ::payload
  (-> (st/spec (su/constrained-map keyword? any?))
      (assoc :name "payload"
             :json-schema/type "map"
             :json-schema/description "event payload"
             :json-schema/indexed false)))


;; Events may need to reference resources that do not follow the CIMI.
;; conventions.  Allow for a more flexible schema to be used here.
(s/def ::href
  (-> (st/spec (s/and string? #(re-matches #"^[a-zA-Z0-9]+[a-zA-Z0-9_./-]*$" %)))
      (assoc :name "href"
             :json-schema/type "string"
             :json-schema/description "reference to associated resource")))


(s/def ::resource
  (-> (st/spec (su/only-keys :req-un [::common/resource-type]
                             :opt-un [::href]))
      (assoc :name "resource"
             :json-schema/type "map"
             :json-schema/description "link to associated resource")))


(s/def ::session-id
  (-> (st/spec ::acl-common/principal)
      (assoc :name "session-id")))


(s/def ::user-id
  (-> (st/spec string?)
      (assoc :name "user-id")))


(s/def ::active-claim ::session/active-claim)


(s/def ::schema
  (-> (st/spec
        (s/and (su/only-keys-maps common/common-attrs
                                  {:req-un [::event-type
                                            ::timestamp
                                            ::category
                                            ::severity
                                            ::resource
                                            ::active-claim]
                                   :opt-un [::session-id
                                            ::user-id
                                            ::subcategory
                                            ::message
                                            ::details
                                            ::payload]})
               (-> (st/spec events-config/event-supported?)
                   (assoc :reason "event type not supported for resource type "
                          :json-schema/type "object"))))
      (assoc :name "event"
             :json-schema/type "object"
             :json-schema/description "event")))
