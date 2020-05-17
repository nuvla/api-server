(ns sixsq.nuvla.server.resources.pricing.utils-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.pricing.stripe :as t]))

(deftest crud-customer
  (let [payment-method-1    (t/create-payment-method {"type"            "sepa_debit"
                                                      "billing_details" {"name"  "toto"
                                                                         "email" "toto@example.com"}
                                                      "sepa_debit"      {"iban" "DE89370400440532013000"}})
        payment-method-1-id (t/get-id payment-method-1)
        cust-1              (t/create-customer {"email"            "toto@example.com"
                                                "payment_method"   payment-method-1-id
                                                "invoice_settings" {"default_payment_method" payment-method-1-id}})
        cust-1-id           (t/get-id cust-1)]
    (is cust-1-id)

    ; test retrieve
    (is (-> cust-1-id
            (t/retrieve-customer)
            (t/get-id)))

    (println (t/create-subscription {"customer" cust-1-id
                                     "items"    [{"plan" "plan_HGQ9iUgnz2ho8e"}
                                                 {"plan" "plan_HGQN0A2ARmEPlB"}]}))

    ;; delete test
    (is (= true (t/get-deleted (t/delete-customer cust-1))))

    ))

