(ns sixsq.nuvla.server.resources.spec.customer-related-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.customer-related :as t]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))

(deftest check-address
  (let [address {:street-address "Av. quelque chose"
                 :city           "Meyrin"
                 :country        "CH"
                 :postal-code    "1217"}]

    (stu/is-valid ::t/address address)

    (stu/is-invalid ::t/address (assoc address :bad "value"))

    ; required
    (doseq [attr #{:street-address :city :country :postal-code}]
      (stu/is-invalid ::t/address (dissoc address attr)))

    ;optional
    (doseq [attr #{}]
      (stu/is-valid ::t/address (dissoc address attr)))))


(deftest check-subscription
  (let [subscription {:plan-id       "price_something"
                      :plan-item-ids ["price_1e332A"
                                      "price_1e332B"]}]

    (stu/is-valid ::t/subscription subscription)

    (stu/is-invalid ::t/subscription (assoc subscription :bad "value"))

    (stu/is-invalid ::t/subscription (assoc subscription :plan-id "xyz"))

    ; required
    (doseq [attr #{:plan-id :plan-item-ids}]
      (stu/is-invalid ::t/subscription (dissoc subscription attr)))

    ;optional
    (doseq [attr #{}]
      (stu/is-valid ::t/address (dissoc subscription attr)))))


(deftest check-customer
  (let [address      {:street-address "Av. quelque chose"
                      :city           "Meyrin"
                      :country        "CH"
                      :postal-code    "1217"}
        subscription {:plan-id       "price_something"
                      :plan-item-ids ["price_1e332A"
                                      "price_1e332B"]}
        customer     {:fullname       "toto"
                      :address        address
                      :subscription   subscription
                      :coupon         "some-coupon-code"
                      :payment-method "pm_something"
                      :email          "a@example.com"}]

    (stu/is-valid ::t/customer customer)

    (stu/is-invalid ::t/customer (assoc customer :bad "value"))

    (stu/is-invalid ::t/customer (assoc customer :fullname ""))

    (stu/is-invalid ::t/customer (assoc-in customer [:subscription :plan-id] "xyz"))

    ; required
    (doseq [attr #{:fullname :address}]
      (stu/is-invalid ::t/customer (dissoc customer attr)))

    ;optional
    (doseq [attr #{:subscription :payment-method :coupon :email}]
      (stu/is-valid ::t/customer (dissoc customer attr)))))
