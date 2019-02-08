(ns sixsq.nuvla.db.filter.parser-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.db.filter.parser :refer :all]
    [instaparse.core :as insta]))

(defn fails-fn
  "Provides a function that will parse a string from the given point
  in the grammar and returns a truthy value if the parsing failed."
  [start]
  (let [parser (insta/parser filter-grammar-url :start start)]
    (fn [s]
      (insta/failure? (parser s)))))

(defn passes-fn
  [start]
  (let [fails (fails-fn start)]
    (fn [s]
      (not (fails s)))))

;; valid double quoted strings
(deftest check-double-quoted-strings
  (are [arg] ((passes-fn :DoubleQuoteString) arg)
             "\"\""
             "\"a\""
             "\"a1\""
             "\"\\\"a\""
             "\"a\\\"\""
             "\"a\\\"a\""
             "\"b0\""
             "\"b-0\""))

;; valid single quoted strings
(deftest check-single-quotes-string
  (are [arg] ((passes-fn :SingleQuoteString) arg)
             "''"
             "'a'"
             "'a1'"
             "'\\'a'"
             "'a\\''"
             "'a\\'a'"
             "'b0'"
             "'b-0'"))

;; valid dates
(deftest check-valid-dates
  (are [arg] ((passes-fn :DateValue) arg)
             "2012-01"
             "2012-01-02"
             "2012-01-02T13:14:25Z"
             "2012-01-02T13:14:25.6Z"
             "2012-01-02T13:14:25-01:15"
             "2012-01-02T13:14:25.6-01:15"
             "2012-01-02T13:14:25+02:30"
             "2012-01-02T13:14:25.6+02:30"))

;; invalid dates
(deftest check-invalid-dates
  (are [arg] ((fails-fn :DateValue) arg)
             "2012"
             "2012-01-99T13:14:25.6ZZ"
             "2012-01-02T13:14:25.6Q"
             "2012-01:02T25:14:25.6-01:15"
             "2012-01-02T13:14:25.6+02-30"))

;; valid filters
(deftest check-valid-filters
  (are [arg] ((passes-fn :Filter) arg)
             "alpha=3"
             "alpha!=3"
             "3=alpha"
             "alpha=3 and beta=4"
             "3=alpha and 4=beta"
             "(alpha=3)"
             "(3=alpha)"
             "alpha=3 and beta=4"
             "alpha=3 or beta=4"
             "alpha=3 and beta=4 or gamma=5 and delta=6"
             "alpha=3 and (beta=4 or gamma=5) and delta=6"
             "b='b0'"
             "b='b-0'"
             "'b0'=b"
             "'b-0'=b"
             "cloud-vm-instanceid='exo:123-456'"
             "alpha=null"
             "null=alpha"
             "alpha!=null"
             "null!=alpha"
             "alpha^='abc'"
             "alpha^=\"abc\""
             "'abc'^=alpha"
             "\"abc\"^=alpha"))

;; invalid filters
(deftest check-invalid-filters
  (are [arg] ((fails-fn :Filter) arg)
             ""
             "()"
             "alpha=beta"
             "alpha=3.2"
             "alpha&&4"
             "alpha>true"
             "alpha>null"
             "alpha^=3"
             "alpha^=null"))

;; invalid filters: all property support removed
(deftest check-invalid-filters
  (are [arg] ((fails-fn :Filter) arg)
             "property['beta']='4'"
             "property['beta']!='4'"
             "property['beta']=null"
             "property['beta']!=null"
             "property[beta]='4'"
             "property['beta']=4"
             "property['beta']<'4'"
             "4=property['beta']"))

;; valid attributes
(deftest check-valid-attributes
  (are [arg] ((passes-fn :Attribute) arg)
             "a"
             "alpha"
             "alpha123"
             "a1"
             "a1/b2"
             "a1/b2/c3"
             "schema-org:attr1"
             "schema-org:attr1/schema-org:attr2"))

;; invalid attributes
(deftest check-invalid-attributes
  (are [arg] ((fails-fn :Attribute) arg)
             ""
             "_"
             "-"
             "/"
             "a-"
             "1"
             "1a"
             "_a"
             "_1"
             "a1/"
             "/a1"
             "a/1"
             ":a"
             "a:"
             "schema-org:a:b"
             "schema-org:a/"))
