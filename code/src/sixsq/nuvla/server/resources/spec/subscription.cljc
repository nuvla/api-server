(ns sixsq.nuvla.server.resources.spec.subscription
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.subscription-config :as subs-conf]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

"
Subscription to actions (defined by 'method-ids') from trigger 'criteria'
against resource 'resource-id'.
"


;; Parent subscription configuration.
(s/def ::parent
  (-> (st/spec ::core/resource-href)
      (assoc :name "parent"
             :json-schema/type "resource-id"
             :json-schema/editable false

             :json-schema/description "Parent subscription configuration resource"
             :json-schema/order 28)))


;; Component to which the subscription is made.
(s/def ::resource-id
  (-> (st/spec ::core/resource-href)
      (assoc :name "resource-id"
             :json-schema/type "resource-id"
             :json-schema/editable false

             :json-schema/description "Subscribed resource id"
             :json-schema/order 29)))


;; Action method.
(s/def ::method-id
  (-> (st/spec ::core/resource-href)
      (assoc :name "method-id"
             :json-schema/type "resource-id"
             :json-schema/editable false

             :json-schema/description "Action method resource id"
             :json-schema/order 23)))


(def attr-to-remove #{::subs-conf/method-ids ::subs-conf/method-id})


(def attributes {:req-un (concat [::parent
                                  ::resource-id
                                  ::method-id]
                                 (remove attr-to-remove (:req-un subs-conf/attributes)))
                 :opt-un (remove attr-to-remove (:opt-un subs-conf/attributes))})


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     attributes))
