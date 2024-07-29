(ns com.sixsq.nuvla.server.resources.spec.callback
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.session-template]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::action
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "action"
             :json-schema/description "name of action"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 10)))


(s/def ::state
  (-> (st/spec #{"WAITING" "FAILED" "SUCCEEDED"})
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/description "current state of callback"

             :json-schema/server-managed true
             :json-schema/order 11

             :json-schema/value-scope {:values  ["WAITING" "FAILED" "SUCCEEDED"]
                                       :default "WAITING"})))


(s/def ::target-resource
  (-> (st/spec ::core/resource-link)
      (assoc :name "target-resource"
             :json-schema/display-name "target resource"
             :json-schema/description "reference to resource affected by callback"

             :json-schema/editable false
             :json-schema/order 12)))


(s/def ::data
  (-> (st/spec (su/constrained-map keyword? any?))
      (assoc :name "data"
             :json-schema/type "map"
             :json-schema/description "data required for callback"

             :json-schema/editable false
             :json-schema/indexed false
             :json-schema/order 13)))


(s/def ::expires
  (-> (st/spec ::core/timestamp)
      (assoc :name "expires"
             :json-schema/description "expiry timestamp for callback action"

             :json-schema/editable false
             :json-schema/order 14)))


(s/def ::tries-left
  (-> (st/spec (s/int-in 0 11))
      (assoc :name "tries-left"
             :json-schema/description "tries left"
             :json-schema/type "integer"
             :json-schema/order 15

             :json-schema/value-scope {:minimum 1
                                       :maximum 10})))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::action
                               ::state]
                      :opt-un [::target-resource
                               ::data
                               ::expires
                               ::tries-left]}))
