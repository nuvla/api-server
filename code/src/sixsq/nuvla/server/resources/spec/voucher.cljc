(ns sixsq.nuvla.server.resources.spec.voucher
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::owner ::cimi-common/resource-link)
(s/def ::user ::cimi-common/resource-link)

(s/def ::amount
  (-> (st/spec double)
      (assoc :name "amount"
             :json-schema/type "double"
             :json-schema/editable true

             :json-schema/display-name "voucher amount"
             :json-schema/description "amount of the voucher"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::currency
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "currency"
             :json-schema/type "string"
             :json-schema/editable true

             :json-schema/display-name "currency"
             :json-schema/description "currency for the voucher amount"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::expiration-date
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "expiration-date"
             :json-schema/type "date-time"
             :json-schema/editable true

             :json-schema/display-name "expiration-date"
             :json-schema/description "expiration-date for the voucher"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::state
  (-> (st/spec #{"new", "activated",
                 "expired", "redeemed"})
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/editable true

             :json-schema/display-name "state"
             :json-schema/description "usage state of the voucher"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive false

             :json-schema/value-scope {:values  ["new", "activated",
                                                 "expired", "redeemed"]
                                       :default "new"})))


(s/def ::service-info
  (-> (st/spec ::cimi-core/url)
      (assoc :name "service-info"
             :json-schema/type "string"
             :json-schema/editable true

             :json-schema/display-name "service-info"
             :json-schema/description "URL for the service provider who issued the voucher"
             :json-schema/group "body"
             :json-schema/order 24
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::code
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "code"
             :json-schema/type "string"
             :json-schema/editable true

             :json-schema/display-name "code"
             :json-schema/description "voucher code"
             :json-schema/group "body"
             :json-schema/order 25
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::activated
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "activated"
             :json-schema/type "date-time"
             :json-schema/editable true

             :json-schema/display-name "activated"
             :json-schema/description "when the voucher was activated"
             :json-schema/group "body"
             :json-schema/order 26
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::redeemed
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "redeemed"
             :json-schema/type "date-time"
             :json-schema/editable true

             :json-schema/display-name "redeemed"
             :json-schema/description "when the voucher was redeemed"
             :json-schema/group "body"
             :json-schema/order 27
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::target-audience
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "target-audience"
             :json-schema/type "string"
             :json-schema/editable true

             :json-schema/display-name "target-audience"
             :json-schema/description "who the voucher is targeted for"
             :json-schema/group "body"
             :json-schema/order 28
             :json-schema/hidden false
             :json-schema/sensitive false)))



(s/def ::batch-reference
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "batch-reference"
             :json-schema/type "string"
             :json-schema/editable true

             :json-schema/display-name "batch-reference"
             :json-schema/description "reference for the batch this voucher belongs to"
             :json-schema/group "body"
             :json-schema/order 29
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::wave-id
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "wave-id"
             :json-schema/type "string"
             :json-schema/editable true

             :json-schema/display-name "wave-id"
             :json-schema/description "optional identifier for the wave when the voucher was issued"
             :json-schema/group "body"
             :json-schema/order 30
             :json-schema/hidden false
             :json-schema/sensitive false)))

;;
;; -------
;;

(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::owner
                               ::amount
                               ::currency
                               ::code
                               ::state
                               ::target-audience]
                      :opt-un [::expiration-date
                               ::activated
                               ::user
                               ::redeemed
                               ::wave-id
                               ::batch-reference]}))