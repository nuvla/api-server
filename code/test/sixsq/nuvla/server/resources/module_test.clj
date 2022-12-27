(ns sixsq.nuvla.server.resources.module-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.resources.module :as module]))

(deftest set-price-test
  (is (= (module/set-price {} "user/jane") {}))
  (with-redefs [pricing-impl/retrieve-price     identity
                pricing-impl/price->map         identity
                module/active-claim->account-id identity
                pricing-impl/create-price       identity
                pricing-impl/get-product        :product
                pricing-impl/get-id             :id]
    (is (= (module/set-price {:price {:cent-amount-daily 10
                                      :currency          "eur"}} "user/jane")
           {:price {:account-id        "user/jane"
                    :cent-amount-daily 10
                    :currency          "eur"
                    :price-id          nil
                    :product-id        nil}})))
  (with-redefs [pricing-impl/retrieve-price     identity
                pricing-impl/price->map         (constantly {:product-id "prod_x"})
                module/active-claim->account-id identity
                pricing-impl/create-price       (fn [{:strs [product]}]
                                                  {:id      "price_x"
                                                   :product product})
                pricing-impl/get-product        :product
                pricing-impl/get-id             :id]
    (is (= (module/set-price {:price {:price-id          "price_x"
                                      :cent-amount-daily 10
                                      :currency          "eur"}} "user/jane")
           {:price {:account-id        "user/jane"
                    :cent-amount-daily 10
                    :currency          "eur"
                    :price-id          "price_x"
                    :product-id        "prod_x"}}))))