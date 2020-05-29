(ns sixsq.nuvla.server.resources.spec.pricing
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(def ^:const plan-id-regex #"^plan_.*")

(defn plan-id? [s] (re-matches plan-id-regex s))

(s/def ::plan-id
  (-> (st/spec (s/and string? plan-id?))
      (assoc :name "plan-id"
             :json-schema/type "string"
             :json-schema/description "identifier of plan id")))


(s/def ::name
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "name"
             :json-schema/type "string"
             :json-schema/description "product plan name")))

(s/def ::order
  (-> (st/spec nat-int?)
      (assoc :name "order"
             :json-schema/type "integer"
             :json-schema/description "hint for visualization order"
             :json-schema/value-scope {:minimum 0
                                       :default 0})))


(s/def ::amount
  (-> (st/spec (s/and number? #(>= % 0)))
      (assoc :name "amount"
             :json-schema/type "double"
             :json-schema/description "amount of the charge")))


(s/def ::currency (-> (st/spec ::core/nonblank-string)
                      (assoc :name "currency"
                             :json-schema/type "string")))


(s/def ::interval (-> (st/spec ::core/nonblank-string)
                      (assoc :name "interval"
                             :json-schema/type "string")))


(s/def ::usage-type (-> (st/spec ::core/nonblank-string)
                        (assoc :name "usage-type"
                               :json-schema/type "string")))


(s/def ::billing-scheme (-> (st/spec ::core/nonblank-string)
                            (assoc :name "billing-scheme"
                                   :json-schema/type "string")))


(s/def ::aggregate-usage (-> (st/spec ::core/nonblank-string)
                             (assoc :name "aggregate-usage"
                                    :json-schema/type "string")))


(s/def ::tiers-mode (-> (st/spec ::core/nonblank-string)
                        (assoc :name "tiers-mode"
                               :json-schema/type "string")))


(s/def ::up-to
  (-> (st/spec (s/nilable nat-int?))
      (assoc :name "up-to"
             :json-schema/type "integer")))


(s/def ::tier
  (-> (st/spec (su/only-keys :req-un [::up-to
                                      ::amount
                                      ::order]))
      (assoc :name "tier"
             :json-schema/type "map")))


(s/def ::tiers
  (-> (st/spec (s/coll-of ::tier))
      (assoc :name "tiers"
             :json-schema/type "array")))


(s/def ::trial-period-days
  (-> (st/spec (s/and number? #(>= % 0)))
      (assoc :name "trial-period-days"
             :json-schema/type "double")))


(s/def ::charge
  (-> (st/spec (su/only-keys
                 :req-un [::currency
                          ::interval
                          ::usage-type
                          ::billing-scheme]
                 :opt-un [::amount
                          ::tiers
                          ::aggregate-usage
                          ::tiers-mode
                          ::trial-period-days]))
      (assoc :name "charge"
             :json-schema/type "map")))


(s/def ::required-items
  (-> (st/spec (s/coll-of ::plan-id))
      (assoc :name "required-items"
             :json-schema/type "array")))


(s/def ::optional-items
  (-> (st/spec (s/coll-of ::plan-id))
      (assoc :name "optional-items"
             :json-schema/type "array")))


(s/def ::product-plan
  (-> (st/spec (su/only-keys
                 :req-un [::plan-id
                          ::name
                          ::charge]
                 :opt-un [::required-items
                          ::optional-items
                          ::order]))
      (assoc :json-schema/type "map")))


(s/def ::product-plan-item
  (-> (st/spec (su/only-keys
                 :req-un [::plan-id
                          ::name
                          ::charge]))
      (assoc :json-schema/type "map")))


(s/def ::plans
  (-> (st/spec (s/coll-of ::product-plan))
      (assoc :name "plans"
             :json-schema/editable false
             :json-schema/server-managed true
             :json-schema/type "array")))


(s/def ::plan-items
  (-> (st/spec (s/coll-of ::product-plan-item))
      (assoc :name "plan-items"
             :json-schema/editable false
             :json-schema/server-managed true
             :json-schema/type "array")))


(def pricing-keys-spec
  {:req-un [::plans
            ::plan-items]})


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     pricing-keys-spec))
