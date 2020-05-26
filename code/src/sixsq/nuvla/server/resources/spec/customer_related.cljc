(ns sixsq.nuvla.server.resources.spec.customer-related
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::fullname
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "fullname"
             :json-schema/display-name "Fullname")))


(def payment-method-id-regex #"^pm_[a-zA-Z0-9]+$")

(s/def ::payment-method
  (-> (st/spec (s/and string? #(re-matches payment-method-id-regex %)))
      (assoc :name "payment-method"
             :json-schema/display-name "Payment method"
             :json-schema/type "string")))


(s/def ::street-address
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "street address"
             :json-schema/display-name "Street address")))

(def country-regex #"^[A-Z]{2}$")

(s/def ::country
  (-> (st/spec (s/and string? #(re-matches country-regex %)))
      (assoc :name "country"
             :json-schema/display-name "Country"
             :json-schema/description "Two-letter country code (ISO 3166-1 alpha-2)"
             :json-schema/type "string")))


(s/def ::postal-code
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "postal-code"
             :json-schema/display-name "Zip/Postal Code")))


(s/def ::city
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "city"
             :json-schema/display-name "City")))


(def plan-id-regex #"^plan_[a-zA-Z0-9]+$")

(s/def ::plan-id
  (-> (st/spec (s/and string? #(re-matches plan-id-regex %)))
      (assoc :name "plan-id"
             :json-schema/display-name "plan id"
             :json-schema/description "subscription plan id"
             :json-schema/type "string")))


(s/def ::plan-item-id
  (-> (st/spec (s/and string? #(re-matches plan-id-regex %)))
      (assoc :name "plan-item-id"
             :json-schema/display-name "plan item id"
             :json-schema/description "subscription plan item id"
             :json-schema/type "string")))


(s/def ::plan-item-ids
  (-> (st/spec (s/coll-of ::plan-item-id))
      (assoc :name "plan-item-ids"
             :json-schema/type "array"
             :json-schema/display-name "plan item ids"
             :json-schema/description "List of subscription plan item ids.")))


(s/def ::address
  (-> (st/spec (su/only-keys-maps {:req-un [::street-address
                                            ::country
                                            ::postal-code
                                            ::city]}))
      (assoc :name "address"
             :json-schema/type "map"
             :json-schema/description "address")))


(s/def ::subscription
  (-> (st/spec (su/only-keys-maps {:req-un [::plan-id
                                            ::plan-item-ids]}))
      (assoc :name "subscription"
             :json-schema/type "map")))


(s/def ::customer
  (su/only-keys-maps {:req-un [::fullname
                               ::address]
                      :opt-un [::subscription
                               ::payment-method]}))
