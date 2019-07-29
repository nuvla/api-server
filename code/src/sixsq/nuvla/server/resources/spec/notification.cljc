(ns sixsq.nuvla.server.resources.spec.notification
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::message
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "message"
             :json-schema/description "notification text"

             :json-schema/order 30)))


(s/def ::category
  (-> (st/spec ::core/kebab-identifier)
      (assoc :name "category"
             :json-schema/description "notification category"

             :json-schema/order 31)))


(s/def ::content-unique-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "content-unique-id"
             :json-schema/display-name "notification content unique id"
             :json-schema/description "notification content unique id"

             :json-schema/order 32)))


(s/def ::expiry
  (-> (st/spec ::core/timestamp)
      (assoc :name "expiry"
             :json-schema/description "notification expiry"

             :json-schema/order 33)))


(s/def ::not-before
  (-> (st/spec ::core/timestamp)
      (assoc :name "not-before"
             :json-schema/display-name "don't show this notification before this time"
             :json-schema/description "don't show this notification before this time"

             :json-schema/order 34)))


(s/def ::target-resource
  (-> (st/spec ::core/resource-href)
      (assoc :name "target-resource"
             :json-schema/display-name "target resource"
             :json-schema/description "link to associated resource"

             :json-schema/editable false
             :json-schema/order 35)))


(s/def ::callback
  (-> (st/spec ::core/resource-href)
      (assoc :name "callback"
             :json-schema/display-name "callback resource"
             :json-schema/description "link to associated callback resource"

             :json-schema/editable false
             :json-schema/order 36)))


(s/def ::callback-msg
  (-> (st/spec string?)
      (assoc :name "callback-msg"
             :json-schema/type "string"
             :json-schema/display-name "callback message"
             :json-schema/description "callback message text"

             :json-schema/order 37)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::message
                               ::category
                               ::content-unique-id]
                      :opt-un [::target-resource
                               ::not-before
                               ::expiry
                               ::callback
                               ::callback-msg]}))
