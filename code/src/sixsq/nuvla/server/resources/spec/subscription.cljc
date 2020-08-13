(ns sixsq.nuvla.server.resources.spec.subscription
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

"
Subscription to events from
* all resources in a collection
* a single resource
* a list of resources of a certain type

State change subscription.

Example 1: any state change on any resource in collection 'infrastructure-service'
{
:collection 'infrastructure-service'
:type 'event'
:category 'state'
:rule '' ; empty or no rule means ANY state change and new resource creation.
}

Example 2: any resource in collection 'infrastructure-service' changing to STARTED or TERMINATED.
{
:collection 'infrastructure-service'
:type 'event'
:category 'state'
:rule 'state=STARTED or state=TERMINATED' ; means change to those states
}
"


(s/def ::collection
  (-> (st/spec string?)
      (assoc :name "collection"
             :json-schema/type "string"
             :json-schema/description "collection of resources to subscribe to")))


(s/def ::resource-ids
  (-> (st/spec (s/coll-of ::common/id :kind vector? :distinct true))
      (assoc :name "resource ids"
             :json-schema/type "array"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/display-name "subscribed resource ids"
             :json-schema/description "List of subscribed resource ids."
             :json-schema/order 25)))

(s/def ::type ())

(s/def ::rule ())

