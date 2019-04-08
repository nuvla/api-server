(ns sixsq.nuvla.server.resources.spec.callback
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.session-template]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::action
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "action"
             :json-schema/name "action"
             :json-schema/type "string"
             :json-schema/editable false

             :json-schema/display-name "action"
             :json-schema/description "name of action"
             :json-schema/order 10)))


(s/def ::state
  (-> (st/spec #{"WAITING" "FAILED" "SUCCEEDED"})
      (assoc :name "state"
             :json-schema/name "state"
             :json-schema/type "string"

             :json-schema/display-name "state"
             :json-schema/description "current state of callback"
             :json-schema/order 11

             :json-schema/value-scope {:values  ["WAITING" "FAILED" "SUCCEEDED"]
                                       :default "WAITING"})))


(s/def ::target-resource
  (-> (st/spec ::cimi-common/resource-link)
      (assoc :name "target-resource"
             :json-schema/name "target-resource"
             :json-schema/type "map"
             :json-schema/editable false

             :json-schema/display-name "target resource"
             :json-schema/description "reference to resource affected by callback"
             :json-schema/order 12)))


(s/def ::data
  (-> (st/spec (su/constrained-map keyword? any?))
      (assoc :name "data"
             :json-schema/name "data"
             :json-schema/type "map"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/display-name "data"
             :json-schema/description "data required for callback"
             :json-schema/order 13)))


(s/def ::expires
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "expires"
             :json-schema/name "expires"
             :json-schema/type "string"
             :json-schema/editable false

             :json-schema/display-name "expires"
             :json-schema/description "expiry timestamp for callback action"
             :json-schema/order 14)))


(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::action
                               ::state]
                      :opt-un [::target-resource
                               ::data
                               ::expires]}))
