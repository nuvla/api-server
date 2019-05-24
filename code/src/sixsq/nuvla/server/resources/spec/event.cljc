(ns sixsq.nuvla.server.resources.spec.event
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::category
  (-> (st/spec #{"state"
                 "alarm"
                 "action"
                 "system"})
      (assoc :name "category"
             :json-schema/type "string"
             :json-schema/description "category of event"

             :json-schema/order 30)))


(s/def ::severity
  (-> (st/spec #{"critical"
                 "high"
                 "medium"
                 "low"})
      (assoc :name "severity"
             :json-schema/type "string"
             :json-schema/description "severity level of event"

             :json-schema/order 31)))


(s/def ::timestamp
  (-> (st/spec ::core/timestamp)
      (assoc :name "timestamp"
             :json-schema/description "time of the event"

             :json-schema/order 32)))


(s/def ::state
  (-> (st/spec string?)
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/description "label of the event's state")))


;; Events may need to reference resources that do not follow the CIMI.
;; conventions.  Allow for a more flexible schema to be used here.
(s/def ::href
  (-> (st/spec (s/and string? #(re-matches #"^[a-zA-Z0-9]+[a-zA-Z0-9_./-]*$" %)))
      (assoc :name "href"
             :json-schema/type "string"
             :json-schema/description "reference to associated resource")))


(s/def ::resource
  (-> (st/spec (su/only-keys :req-un [::href]))
      (assoc :name "resource"
             :json-schema/type "map"
             :json-schema/description "link to associated resource")))


(s/def ::content
  (-> (st/spec (su/only-keys :req-un [::resource ::state]))
      (assoc :name "content"
             :json-schema/type "map"
             :json-schema/description "content describing event"

             :json-schema/order 33)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::timestamp
                               ::content
                               ::category
                               ::severity]}))
