(ns sixsq.nuvla.server.resources.spec.voucher-report
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::supplier (-> (st/spec ::user/id)
                      (assoc :name "supplier"
                             :json-schema/type "resource-id"
                             :json-schema/description "reference to supplier user id"

                             :json-schema/section "meta"
                             :json-schema/group "body"
                             :json-schema/order 18)))


(s/def ::amount-left
  (-> (st/spec (s/and number?))
      (assoc :name "amount-left"
             :json-schema/type "double"

             :json-schema/description "amount left from the voucher"
             :json-schema/group "body"
             :json-schema/order 19)))


(s/def ::amount-spent
  (-> (st/spec (s/and number? #(>= % 0)))
      (assoc :name "amount-spent"
             :json-schema/type "double"

             :json-schema/description "amount spent from the voucher"
             :json-schema/group "body"
             :json-schema/order 20)))


(s/def ::currency
  (-> (st/spec #{"EUR", "CHF", "USD"})
      (assoc :name "currency"
             :json-schema/type "string"
             :json-schema/value-scope {:values ["EUR", "CHF", "USD"]}

             :json-schema/description "currency for the voucher amount"
             :json-schema/group "body"
             :json-schema/order 21)))


(s/def ::redeemed
  (-> (st/spec ::core/timestamp)
      (assoc :name "redeemed"
             :json-schema/type "date-time"

             :json-schema/description "when the voucher was redeemed, from the supplier side"
             :json-schema/group "body"
             :json-schema/order 22)))

;;
;; -------
;;

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::common/parent
                               ::amount-spent
                               ::supplier]
                      :opt-un [::amount-left
                               ::currency
                               ::redeemed]}))