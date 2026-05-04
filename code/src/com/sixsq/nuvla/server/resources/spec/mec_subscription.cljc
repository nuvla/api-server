 (ns com.sixsq.nuvla.server.resources.spec.mec-subscription
   (:require
     [clojure.spec.alpha :as s]
     [com.sixsq.nuvla.server.resources.spec.common :as common]
     [com.sixsq.nuvla.server.resources.spec.core :as core]
     [com.sixsq.nuvla.server.util.spec :as su]
     [spec-tools.core :as st]))

 ;; Notification subscription type
 (s/def ::subscription-type
   (-> (st/spec #{"AppInstanceStateChangeNotification"
                  "AppLcmOpOccStateChangeNotification"})
       (assoc :name "subscription type"
              :json-schema/type "string"
              :json-schema/description "ETSI MEC lifecycle notification type"
              :json-schema/order 20)))

 (s/def ::callback-uri
   (-> (st/spec (s/and string? #(re-matches #"https?://.*" %)))
       (assoc :name "callback URI"
              :json-schema/type "string"
              :json-schema/description "HTTP(S) endpoint receiving MEC notifications"
              :json-schema/order 21)))

 (s/def ::owner
   (-> (st/spec ::core/resource-href)
       (assoc :name "owner"
              :json-schema/type "resource-id"
              :json-schema/description "owner of the MEC subscription"
              :json-schema/order 22)))

 (s/def ::active
   (-> (st/spec boolean?)
       (assoc :name "active"
              :json-schema/type "boolean"
              :json-schema/description "whether the subscription is active"
              :json-schema/order 23)))

 ;; App instance filter
 (s/def ::app-instance-id
   (-> (st/spec ::core/resource-href)
       (assoc :name "app instance id"
              :json-schema/type "resource-id"
              :json-schema/description "specific app instance to match"
              :json-schema/order 30)))

 (s/def ::app-name
   (-> (st/spec string?)
       (assoc :name "app name"
              :json-schema/type "string"
              :json-schema/description "application name filter"
              :json-schema/order 31)))

 (s/def ::operational-state
   (-> (st/spec #{"STARTED" "STOPPED" "UNKNOWN"})
       (assoc :name "operational state"
              :json-schema/type "string"
              :json-schema/description "operational state filter"
              :json-schema/order 32)))

 (s/def ::instantiation-state
   (-> (st/spec #{"NOT_INSTANTIATED" "INSTANTIATED"})
       (assoc :name "instantiation state"
              :json-schema/type "string"
              :json-schema/description "instantiation state filter"
              :json-schema/order 33)))

 (s/def ::app-instance-filter
   (-> (st/spec (su/only-keys-maps {:opt-un [::app-instance-id
                                             ::app-name
                                             ::operational-state
                                             ::instantiation-state]}))
       (assoc :name "app instance filter"
              :json-schema/type "map"
              :json-schema/description "filter criteria for AppInstance notifications"
              :json-schema/order 34)))

 ;; Operation occurrence filter
 (s/def ::operation-type
   (-> (st/spec #{"INSTANTIATE" "TERMINATE" "OPERATE"})
       (assoc :name "operation type"
              :json-schema/type "string"
              :json-schema/description "lifecycle operation type filter"
              :json-schema/order 40)))

 (s/def ::operation-state
   (-> (st/spec #{"STARTING" "PROCESSING" "COMPLETED" "FAILED" "ROLLED_BACK"})
       (assoc :name "operation state"
              :json-schema/type "string"
              :json-schema/description "operation state filter"
              :json-schema/order 41)))

 (s/def ::app-lcm-op-occ-filter
   (-> (st/spec (su/only-keys-maps {:opt-un [::app-instance-id
                                             ::operation-type
                                             ::operation-state]}))
       (assoc :name "operation occurrence filter"
              :json-schema/type "map"
              :json-schema/description "filter criteria for AppLcmOpOcc notifications"
              :json-schema/order 42)))

 (def attributes
   {:req-un [::subscription-type
             ::callback-uri
             ::owner
             ::active]
    :opt-un [::app-instance-filter
             ::app-lcm-op-occ-filter]})

 (s/def ::schema
   (su/only-keys-maps common/common-attrs
                      attributes))
