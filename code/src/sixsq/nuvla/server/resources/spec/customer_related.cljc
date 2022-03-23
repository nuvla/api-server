(ns sixsq.nuvla.server.resources.spec.customer-related
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::fullname
  (assoc (st/spec ::core/nonblank-string)
    :name "fullname"
    :json-schema/display-name "Fullname"))


(def payment-method-id-regex #"^pm_[a-zA-Z0-9]+$")

(s/def ::payment-method
  (assoc (st/spec (s/and string? #(re-matches payment-method-id-regex %)))
    :name "payment-method"
    :json-schema/display-name "Payment method"
    :json-schema/type "string"))


(s/def ::coupon
  (assoc (st/spec ::core/nonblank-string)
    :name "coupon"
    :json-schema/display-name "Coupon"
    :json-schema/type "string"))


(s/def ::street-address
  (assoc (st/spec ::core/nonblank-string)
    :name "street address"
    :json-schema/display-name "Street address"))

(def country-regex #"^[A-Z]{2}$")

(s/def ::country
  (assoc (st/spec (s/and string? #(re-matches country-regex %)))
    :name "country"
    :json-schema/display-name "Country"
    :json-schema/description "Two-letter country code (ISO 3166-1 alpha-2)"
    :json-schema/type "string"))


(s/def ::postal-code
  (assoc (st/spec ::core/nonblank-string)
    :name "postal-code"
    :json-schema/display-name "Zip/Postal Code"))


(s/def ::city
  (assoc (st/spec ::core/nonblank-string)
    :name "city"
    :json-schema/display-name "City"))


(def price-id-regex #"^price_.+$")

(s/def ::plan-id
  (assoc (st/spec (s/and string? #(re-matches price-id-regex %)))
    :name "plan-id"
    :json-schema/display-name "plan id"
    :json-schema/description "subscription plan id"
    :json-schema/type "string"))


(s/def ::plan-item-id
  (assoc (st/spec (s/and string? #(re-matches price-id-regex %)))
    :name "plan-item-id"
    :json-schema/display-name "plan item id"
    :json-schema/description "subscription plan item id"
    :json-schema/type "string"))

(s/def ::trial-days
  (assoc (st/spec (s/int-in 0 101))
    :name "trial-days"
    :json-schema/type "integer"
    :json-schema/editable false
    :json-schema/server-managed true))

(s/def ::plan-item-ids
  (assoc (st/spec (s/coll-of ::plan-item-id))
    :name "plan-item-ids"
    :json-schema/type "array"
    :json-schema/display-name "plan item ids"
    :json-schema/description "List of subscription plan item ids."))


(s/def ::address
  (assoc (st/spec (su/only-keys-maps {:req-un [::street-address
                                               ::country
                                               ::postal-code
                                               ::city]}))
    :name "address"
    :json-schema/type "map"
    :json-schema/description "address"))

(s/def ::email
  (assoc (st/spec ::core/email)
    :name "email"))


(s/def ::subscription
  (assoc (st/spec (su/only-keys-maps {:req-un [::plan-id
                                               ::plan-item-ids]
                                      :opt-un [::trial-days]}))
    :name "subscription"
    :json-schema/type "map"))


(s/def ::customer
  (assoc (st/spec (su/only-keys-maps {:req-un [::fullname
                                               ::address]
                                      :opt-un [::subscription
                                               ::payment-method
                                               ::coupon
                                               ::email]}))
    :name "customer"
    :json-schema/type "map"))
