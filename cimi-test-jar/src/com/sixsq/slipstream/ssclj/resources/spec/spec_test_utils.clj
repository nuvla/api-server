(ns com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.spec.alpha :as s]
    [clojure.test :refer [is]]
    [expound.alpha :as expound]))


(defmacro is-valid
  "Verifies that the form conforms to the spec with s/valid?. If it does not,
   the message will the the analysis from expound."
  [spec form]
  `(is (true? (s/valid? ~spec ~form)) (expound/expound-str ~spec ~form)))


(defmacro is-invalid
  "Verifies that the form does not conform to the spec with s/valid?. If it
   does, then the message is the pretty-printed representation of the form."
  [spec form]
  `(is (false? (s/valid? ~spec ~form)) (with-out-str (pprint ~form))))
