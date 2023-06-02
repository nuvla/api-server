(ns sixsq.nuvla.server.resources.module.utils-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.module.utils :as t]))

(deftest full-uuid->uuid
  (is (= "f3ab4193-ff72-4947-b67b-0f9a448fe1c7" (t/full-uuid->uuid "f3ab4193-ff72-4947-b67b-0f9a448fe1c7_1"))))

(deftest full-uuid->index
  (is (= 1 (t/full-uuid->version-index "a_1")))
  (is (nil? (t/full-uuid->version-index "a"))))

(deftest get-content-id
  (let [module-meta {:versions [{:href "a"}
                                nil
                                {:href "c"}
                                {:href "d"}
                                {:href "e"}
                                {:href "f"}
                                {:href "g"}]}]
    (are [expected i] (= expected (t/get-content-id module-meta i))
                      "d" 3
                      "a" 0
                      "g" 6)
    (is (thrown?
          NullPointerException
          (t/get-content-id module-meta nil)))
    (is (thrown?
          IndexOutOfBoundsException
          (t/get-content-id module-meta 10)))))


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
  (is (= (t/set-price {} nil "user/jane") {}))
  (with-redefs [pricing-impl/retrieve-price identity
                pricing-impl/price->map     identity
                t/active-claim->account-id  identity
                pricing-impl/create-price   identity
                pricing-impl/get-product    :product
                pricing-impl/get-id         :id]
    (is (= (t/set-price {:price {:cent-amount-daily 10
                                 :currency          "eur"}}
                        nil "user/jane")
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
    (is (= (t/set-price {:price {:price-id          "price_z"
                                 :cent-amount-daily 10
                                 :currency          "eur"}}
                        {:price-id          "price_x"
                         :cent-amount-daily 20
                         :currency          "eur"} "user/jane")
           {:price {:account-id        "user/jane"
                    :cent-amount-daily 10
                    :currency          "eur"
                    :price-id          "price_x"
                    :product-id        "prod_x"}}))))

(deftest collect-applications-hrefs
  (are [expected applications-sets]
    (= expected (t/collect-applications-hrefs applications-sets))
    [] nil
    [] {}
    ["module/x_0"] [{:name         "x"
                     :applications [{:id      "module/x"
                                     :version 0}]}]
    ["module/a_0"
     "module/b_1"
     "module/c_2"] [{:name         "x"
                     :applications [{:id      "module/a"
                                     :version 0}
                                    {:id      "module/b"
                                     :version 1}]}
                    {:name         "x"
                     :applications [{:id      "module/c"
                                     :version 2}]}]
    ["module/a_0"
     "module/b_1"
     "module/c_2"] [{:name         "x"
                     :applications [{:id      "module/a"
                                     :version 0}
                                    {:id      "module/b"
                                     :version 1}]}
                    {:name         "x"
                     :applications [{:id      "module/a"
                                     :version 0}
                                    {:id      "module/c"
                                     :version 2}]}]))

(deftest collect-applications-hrefs
  (are [expected arg]
    (= expected (t/inject-resolved-applications (first arg) (second arg)))
    nil
    [nil nil]

    {}
    [{} {}]

    {:applications-sets [{:applications [{:id       "module/a"
                                          :version  0
                                          :resolved {:name "module_a v 0"}}
                                         {:id      "module/c"
                                          :version 2}]
                          :name         "x"}]}
    [{"module/a_0" {:name "module_a v 0"}
      "module/c_0" {:name "module_c v 0"}}
     {:applications-sets
      [{:name         "x"
        :applications [{:id      "module/a"
                        :version 0}
                       {:id      "module/c"
                        :version 2}]}]}]

    {:applications-sets [{:applications [{:id       "module/a"
                                          :version  0
                                          :resolved {:name "module_a v 0"}}
                                         {:id       "module/c"
                                          :version  2
                                          :resolved {:name "module_c v 2"}}]
                          :name         "x"}]}
    [{"module/a_0" {:name "module_a v 0"}
      "module/b_1" {:name "module_b v 1"}
      "module/c_2" {:name "module_c v 2"}}
     {:applications-sets
      [{:name         "x"
        :applications [{:id      "module/a"
                        :version 0}
                       {:id      "module/c"
                        :version 2}]}]}
     {:applications-sets [{:applications [{:id       "module/a"
                                           :version  0
                                           :resolved {:name "module_a v 0"}}
                                          {:id       "module/b"
                                           :version  1
                                           :resolved {:name "module_b v 1"}}
                                          {:id      "module/c"
                                           :version 2}]
                           :name         "x"}]}]))
