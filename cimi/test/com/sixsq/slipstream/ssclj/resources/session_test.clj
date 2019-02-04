(ns com.sixsq.slipstream.ssclj.resources.session-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]))

(deftest check-is-content-type?
  (are [expected-fn input] (expected-fn (u/is-content-type? input))
                           true? :content-type
                           true? "content-type"
                           true? "Content-Type"
                           true? "CONTENT-TYPE"
                           true? "CoNtEnT-TyPe"
                           false? 1234
                           false? nil))

(deftest check-is-form?
  (are [expected-fn input] (expected-fn (u/is-form? input))
                           true? {:content-type u/form-urlencoded}
                           true? {"content-type" u/form-urlencoded}
                           false? {:content-type "application/json"}
                           false? {"content-type" "application/json"}))

(deftest check-convert-form
  (is (= {:sessionTemplate {:alpha "alpha", :beta "beta"}}
         (u/convert-form :sessionTemplate {:alpha "alpha", "beta" "beta"}))))
