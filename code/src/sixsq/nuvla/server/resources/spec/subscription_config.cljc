(ns sixsq.nuvla.server.resources.spec.subscription-config
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

"
Subscription to actions (defined by 'method-id') from trigger 'criteria'
against resources of 'resource-kind' subject to filter 'resource-filter'.

Example:

{
  ;; General
  :id 'subscription/<uuid>'
  :enabled #{true false}
  :acl {:owners ['user/01']}

  ;; Subscription category
  :category #{notification, bill, etc.}

  ;; Action method and schedule
	:method-id 'notification-conf/<uuid>'
	:schedule {} ; open map defining the scheduling policy

	;; Component
	:resource-kind #{nuvlabox-state|module|deployment|data-*|infrastructure-service|..}
	:resource-filter \"tags='foo'\"

	;; Criteria
	:criteria {
	  :kind #{metric, event}
	  :metric <load|ram|disk|..>
	  :condition #{<, >, =, !=}
	  :value_% 1.2 ; float
	  :window 60 ; in seconds
	}
}
"

;; General.
(s/def ::enabled
  (-> (st/spec boolean?)
      (assoc :name "enabled"
             :json-schema/type "string"
             :json-schema/description "subscription enabled or disabled"
             :json-schema/order 21)))


;; Subscription category.
(s/def ::category
  (-> (st/spec #{"notification" "bill"})
      (assoc :name "category"
             :json-schema/type "string"
             :json-schema/description "category of the subscription"
             :json-schema/order 22)))


;; Action method and schedule.
(s/def ::method-id
  (-> (st/spec ::core/resource-href)
      (assoc :name "method-id"
             :json-schema/type "resource-id"
             :json-schema/editable false

             :json-schema/description "Action method resource id"
             :json-schema/order 23)))


(s/def ::schedule
  (-> (st/spec map?)
      (assoc :name "schedule"
             :json-schema/type "map"
             :json-schema/description "rule for scheduling the action"
             :json-schema/order 24)))


;; Components to which the subscription is made.
(s/def ::resource-kind
  (-> (st/spec string?)
      (assoc :name "resource-kind"
             :json-schema/type "string"
             :json-schema/description "resource collection"
             :json-schema/order 25)))


(s/def ::resource-filter
  (-> (st/spec string?)
      (assoc :name "resource-filter"
             :json-schema/type "resource-id"
             :json-schema/description "Filter for resource in resource-kind"
             :json-schema/order 26)))


;; Triggering criteria via matching rules.
(s/def ::kind
  (-> (st/spec #{"boolean" "set" "numeric"})
      (assoc :name "kind"
             :json-schema/type "string"
             :json-schema/description "kind of the criteria"
             :json-schema/order 30)))


(s/def ::metric
  (-> (st/spec string?)
      (assoc :name "metric"
             :json-schema/type "string"
             :json-schema/description "metric name"
             :json-schema/order 31)))


(s/def ::value
  (-> (st/spec string?)
      (assoc :name "value"
             :json-schema/type "string"
             :json-schema/description "value as string"
             :json-schema/order 32)))


(s/def ::value-type
  (-> (st/spec #{"boolean" "string" "double" "integer"})
      (assoc :name "value-type"
             :json-schema/type "string"
             :json-schema/description "value type hint"
             :json-schema/order 33)))


(s/def ::condition
  (-> (st/spec string?)
      (assoc :name "condition"
             :json-schema/type "string"
             :json-schema/description "condition of the trigger <=|!=|>|<|any|avg>"
             :json-schema/order 34)))

(s/def ::window
  (-> (st/spec integer?)
      (assoc :name "window"
             :json-schema/type "integer"
             :json-schema/description "time windows in seconds for the condition to hold"
             :json-schema/order 35)))


(s/def ::criteria
  (-> (st/spec (su/only-keys-maps {:req-un [::kind
                                            ::metric
                                            ::value
                                            ::condition]
                                   :opt-un [::window
                                            ::value-type]}))
      (assoc :name "criteria"
             :json-schema/type "map"
             :json-schema/description "Triggering criteria via matching rules."
             :json-schema/order 27)))


(def attributes {:req-un [::enabled
                          ::category
                          ::method-id
                          ::resource-kind
                          ::resource-filter
                          ::criteria]
                 :opt-un [::schedule]})

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     attributes))
