(ns sixsq.nuvla.server.resources.module.utils-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.module.utils :as t]))


(deftest split-resource
  (is (= [{:alpha 1, :beta 2} {:gamma 3}]
         (t/split-resource {:alpha   1
                            :beta    2
                            :content {:gamma 3}}))))


(deftest check-parent-path
  (are [parent-path path] (= parent-path (t/get-parent-path path))
                          nil nil
                          "" "alpha"
                          "alpha" "alpha/beta"
                          "alpha/beta" "alpha/beta/gamma"))


(deftest check-set-parent-path
  (are [expected arg] (= expected (:parent-path (t/set-parent-path arg)))
                      "ok" {:parent-path "bad-value"
                            :path        "ok/go"}
                      nil {}))

(deftest active-claim->account-id
  (with-redefs [crud/query-as-admin (constantly [nil []])]
   (is (thrown-with-msg?
         Exception
         #"unable to resolve vendor account-id for active-claim 'user/jane'"
         (t/active-claim->account-id "user/jane"))))
  (with-redefs [crud/query-as-admin (constantly [nil [{:account-id "acct_x"}]])]
    (is (= (t/active-claim->account-id "user/jane") "acct_x"))))

(deftest resolve-vendor-email
  (let [module-meta {:price {:account-id "acct_x"}}
        email       "jane@example.com"]
    (with-redefs [crud/query-as-admin (constantly [nil [{}]])]
      (is (= (t/resolve-vendor-email module-meta)
            module-meta)))
    (with-redefs [crud/query-as-admin (constantly [nil [{:email email}]])]
      (is (= (t/resolve-vendor-email module-meta)
             (assoc-in module-meta [:price :vendor-email] email))))))

(deftest set-price-test
  (is (= (t/set-price {} "user/jane") {}))
  (with-redefs [pricing-impl/retrieve-price identity
                pricing-impl/price->map     identity
                t/active-claim->account-id  identity
                pricing-impl/create-price   identity
                pricing-impl/get-product    :product
                pricing-impl/get-id         :id]
    (is (= (t/set-price {:price {:cent-amount-daily 10
                                 :currency          "eur"}} "user/jane")
           {:price {:account-id        "user/jane"
                    :cent-amount-daily 10
                    :currency          "eur"
                    :price-id          nil
                    :product-id        nil}})))
  (with-redefs [pricing-impl/retrieve-price identity
                pricing-impl/price->map     (constantly {:product-id "prod_x"})
                t/active-claim->account-id  identity
                pricing-impl/create-price   (fn [{:strs [product]}]
                                              {:id      "price_x"
                                               :product product})
                pricing-impl/get-product    :product
                pricing-impl/get-id         :id]
    (is (= (t/set-price {:price {:price-id          "price_x"
                                 :cent-amount-daily 10
                                 :currency          "eur"}} "user/jane")
           {:price {:account-id        "user/jane"
                    :cent-amount-daily 10
                    :currency          "eur"
                    :price-id          "price_x"
                    :product-id        "prod_x"}}))))
