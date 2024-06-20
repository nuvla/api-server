(ns sixsq.nuvla.server.resources.spec.event
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(def -category-values
  ["add"
   "edit"
   "delete"
   "action"
   "state"
   "alarm"
   "email"
   "user"])

(s/def ::category
  (-> (st/spec (set -category-values))
      (assoc :name "category"
             :json-schema/type "string"
             :json-schema/description "category of event"
             :json-schema/value-scope {:values -category-values}

             :json-schema/order 30)))

(def -severity-values
  ["critical"
   "high"
   "medium"
   "low"])

(s/def ::severity
  (-> (st/spec (set -severity-values))
      (assoc :name "severity"
             :json-schema/type "string"
             :json-schema/description "severity level of event"
             :json-schema/value-scope {:values -severity-values}

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
  (-> (st/spec (s/nilable ::core/nonblank-string))
      (assoc :name "href"
             :json-schema/type "string"
             :json-schema/description "reference to associated resource")))


(s/def :resource/content
  (-> (st/spec map?)
      (assoc :name "content"
             :json-schema/type "map"
             :json-schema/description "arbitrary content of the resource"
             :json-schema/indexed false)))


(s/def ::resource
  (-> (st/spec (su/only-keys :req-un [::href]
                             :opt-un [:resource/content]))
      (assoc :name "resource"
             :json-schema/type "map"
             :json-schema/description "link to associated resource")))


(s/def ::identifier
  (-> (st/spec string?)
      (assoc :name "identifier"
             :json-schema/type "string"
             :json-schema/description "identifier")))


(s/def ::linked-identifiers
  (-> (st/spec (s/coll-of ::identifier))
      (assoc :name "linked-identifiers"
             :json-schema/type "array"
             :json-schema/description "list of linked identifiers")))

(s/def ::content
  (-> (st/spec (su/only-keys :req-un [::resource]
                             :opt-un [::linked-identifiers
                                      ::state]))
      (assoc :name "content"
             :json-schema/type "map"
             :json-schema/description "content describing event"

             :json-schema/order 33)))


(s/def ::user-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "user-id"
             :json-schema/type "string"
             :json-schema/description "user id")))


(s/def ::claims
  (-> (st/spec (s/coll-of ::core/nonblank-string))
      (assoc :name "claims"
             :json-schema/type "array"
             :json-schema/description "claims")))


(s/def ::authn-info
  (-> (st/spec (su/only-keys :opt-un [::user-id ::session/active-claim ::claims]))
      (assoc :name "authn-info"
             :json-schema/type "map"
             :json-schema/description "authentication info"

             :json-schema/order 34)))


(s/def ::success
  (-> (st/spec boolean?)
      (assoc :name "success"
             :json-schema/type "boolean"
             :json-schema/description "success"

             :json-schema/order 35)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::timestamp
                               ::content
                               ::category
                               ::severity]
                      :opt-un [::authn-info
                               ::success]}))
