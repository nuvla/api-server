(ns sixsq.nuvla.server.resources.spec.subscription
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

"
Subscription to a notification on events of certain category of a resource.

TODO: update documentation.

Example:

{
	:type 'notification',
	# what
	:kind 'event',
	:category 'state',
	:resources 'deployment/01',
	# how
	:notification 'notification/02',
	# ACL
	:acl {
	    :owners ['user/01']
	    }
}
"

(s/def ::type
  (-> (st/spec string?)
      (assoc :name "type"
             :json-schema/type "string"
             :json-schema/description "type of the subscription"
             :json-schema/order 20)))

;; Subscription to what.

(s/def ::kind
  (-> (st/spec string?)
      (assoc :name "kind"
             :json-schema/type "string"
             :json-schema/description "kind of the subscription"
             :json-schema/order 20)))

(s/def ::category
  (-> (st/spec string?)
      (assoc :name "category"
             :json-schema/type "string"
             :json-schema/description "category of the kind"
             :json-schema/order 20)))

(s/def ::resource
  (-> (st/spec ::core/resource-href)
      (assoc :name "resource id"
             :json-schema/type "resource-id"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/description "Subscribed resource id."
             :json-schema/order 25)))

;; What to do.

(s/def ::notification
  (-> (st/spec ::core/resource-href)
      (assoc :name "notification"
             :json-schema/type "resource-id"
             :json-schema/description "Notification ID."
             :json-schema/order 20)))

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::type
                               ::kind
                               ::category
                               ::resource
                               ::notification]}))

