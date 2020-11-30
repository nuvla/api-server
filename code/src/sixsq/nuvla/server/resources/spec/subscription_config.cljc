(ns sixsq.nuvla.server.resources.spec.subscription-config
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

"
Configuration of subscriptions.
"

(s/def ::type
  (-> (st/spec #{"notification"})
      (assoc :name "type"
             :json-schema/type "string"
             :json-schema/description "Type of the subscription"
             :json-schema/order 20)))


(s/def ::collection
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "collection"
             :json-schema/type "string"
             :json-schema/description "Collection of resources to subscribe to"
             :json-schema/order 21)))


(s/def ::category
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "category"
             :json-schema/type "string"
             :json-schema/description "Field of the document in the collection"
             :json-schema/order 23)))


(s/def ::enabled
  (-> (st/spec boolean?)
      (assoc :name "enabled"
             :json-schema "boolean"
             :json-schema/description "Configuration enabled or disabled."
             :json-schema/order 24)))


(s/def ::method
  (-> (st/spec ::core/resource-href)
      (assoc :name "method"
             :json-schema/type "resource-id"
             :json-schema/description "Reference to the default action method"
             :json-schema/order 25)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::type
                               ::collection
                               ::category
                               ::enabled
                               ::method]}))


