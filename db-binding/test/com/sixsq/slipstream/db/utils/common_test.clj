(ns com.sixsq.slipstream.db.utils.common-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.db.utils.common :as t]))


(deftest test-camelcase
  (is (= "" (t/de-camelcase "")))
  (is (= "abc" (t/de-camelcase "Abc")))
  (is (= "abc-def" (t/de-camelcase "AbcDef")))

  ;; given string must be lisp-cased, if not empty string returned
  (is (= "" (t/lisp-to-camelcase "Abc")))
  (is (= "" (t/lisp-to-camelcase "-")))
  (is (= "" (t/lisp-to-camelcase "abc-")))
  (is (= "" (t/lisp-to-camelcase "abc--def")))
  (is (= "" (t/lisp-to-camelcase "abc-Def-ghi")))
  (is (= "" (t/lisp-to-camelcase "")))

  (is (= "Abc" (t/lisp-to-camelcase "abc")))
  (is (= "AbcDef" (t/lisp-to-camelcase "abc-def"))))


(deftest check-split-id
  (are [expected id] (= expected (t/split-id id))
                     nil nil
                     ["cloud-entry-point" "cloud-entry-point"] "cloud-entry-point"
                     ["cloud-entry-point" "cloud-entry-point"] "cloud-entry-point/"
                     ["alpha-beta" "one-two"] "alpha-beta/one-two"))
