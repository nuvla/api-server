(ns com.sixsq.nuvla.server.resources.spec.customer-related
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
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


(s/def ::subscription?
  (assoc (st/spec boolean?)
    :name "subscription?"
    :json-schema/type "boolean"
    :json-schema/description "add a subscription on creation?"))


(s/def ::customer
  (assoc (st/spec (su/only-keys-maps {:req-un [::fullname
                                               ::address]
                                      :opt-un [::subscription?
                                               ::payment-method
                                               ::coupon
                                               ::email]}))
    :name "customer"
    :json-schema/type "map"))
