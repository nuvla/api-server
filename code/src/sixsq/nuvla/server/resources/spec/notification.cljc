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
             :json-schema/type "string"
             :json-schema/description "notification text"

             :json-schema/order 30)))


(s/def ::type
  (-> (st/spec ::core/kebab-identifier)
      (assoc :name "type"
             :json-schema/description "notification type"

             :json-schema/order 31)))


(s/def ::content-unique-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "content-unique-id"
             :json-schema/type "string"
             :json-schema/description "notification content unique id"

             :json-schema/order 32)))


(s/def ::expiry
  (-> (st/spec ::core/timestamp)
      (assoc :name "expiry"
             :json-schema/description "notification expiry"

             :json-schema/order 33)))


(s/def ::hide-until
  (-> (st/spec ::core/timestamp)
      (assoc :name "hide-until"
             :json-schema/description "hide notification till defined time"

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


; TODO: check if needed.
(s/def ::callback-msg
  (-> (st/spec string?)
      (assoc :name "callback-msg"
             :json-schema/type "string"
             :json-schema/description "callback message text"

             :json-schema/order 37)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::message
                               ::type
                               ::content-unique-id]
                      :opt-un [::target-resource
                               ::hide-until
                               ::expiry
                               ::callback
                               ::callback-msg]}))
