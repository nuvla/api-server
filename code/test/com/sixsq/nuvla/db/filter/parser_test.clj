(ns com.sixsq.nuvla.db.filter.parser-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [are deftest is]]
    [com.sixsq.nuvla.db.filter.parser :as t]
    [instaparse.core :as insta]))


(defn fails-fn
  "Provides a function that will parse a string from the given point
  in the grammar and returns a truthy value if the parsing failed."
  [start]
  (let [parser (insta/parser t/filter-grammar-url :start start)]
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
(deftest check-invalid-property-filters
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


(deftest check-invalid-wkt
  (are [arg] ((fails-fn :WktValue) arg)
             "2012"
             "2012-01-99T13:14:25.6ZZ"
             "2012-01-02T13:14:25.6Q"
             "2012-01:02T25:14:25.6-01:15"
             "2012-01-02T13:14:25.6+02-30"))

(def expected-transform-or-query
  [:Filter
   [:Or
    [:And [:Comp [:Attribute "id"] [:EqOp "="] [:NullValue "null"]]]
    [:And [:Comp [:Attribute "b"] [:EqOp "="] [:BoolValue "true"]]]
    [:And [:Comp [:Attribute "c"] [:EqOp "="] [:IntValue "1"]]]
    [:And [:Comp [:Attribute "d"] [:EqOp "="] [:StringValue "'foo'"]]]]])

(deftest check-parse-cimi-filter-transform-or
  (is (= expected-transform-or-query (t/parse-cimi-filter "id=null or b=true or c=1 or d='foo'"))))


(def expected-transform-values-query
  [:Filter
   [:Or
    [:And
     [:Comp [:Attribute "id"] [:EqOp "="]
      [:Values
       [:StringValue "\"a\""]
       [:StringValue "\"b\""]
       [:IntValue "1"]]]]]])

(deftest check-parse-cimi-filter-transform-values-query
  (is (= [:Filter
          [:Or
           [:And
            [:Comp [:Attribute "id"] [:EqOp "="]
             [:Values]]]]] (t/parse-cimi-filter "id=[]")))
  (is (= expected-transform-values-query (t/parse-cimi-filter "id=[\"a\" \"b\" 1]"))))

(def expected-transform-and-query
  [:Filter
   [:Or
    [:And
     [:Comp [:Attribute "id"] [:EqOp "="] [:NullValue "null"]]
     [:Comp [:Attribute "b"] [:EqOp "="] [:BoolValue "true"]]
     [:Comp [:Attribute "c"] [:EqOp "="] [:IntValue "1"]]]]])

(deftest check-parse-cimi-filter-transform-and-query
  (is (= expected-transform-and-query (t/parse-cimi-filter "id=null and b=true and c=1"))))

(def expected-transform-complex-query
  [:Filter
   [:Or
    [:And
     [:Or
      [:And [:Comp [:Attribute "id"] [:EqOp "="] [:NullValue "null"]]]
      [:And [:Comp [:StringValue "'x'"] [:EqOp "="] [:Attribute "id"]]]
      [:And
       [:Comp [:Attribute "d"] [:EqOp "="] [:IntValue "1"]]
       [:Comp [:Attribute "b"] [:EqOp "="] [:BoolValue "true"]]]]
     [:Comp [:Attribute "name"] [:EqOp "!="] [:StringValue "\"s\""]]
     [:Comp [:Attribute "acl" "/" "owner"] [:EqOp "="] [:StringValue "'user/1'"]]
     [:Comp [:Attribute "attr"] [:GeoOp "intersects"] [:WktValue [:StringValue "'POINT(1 2)'"]]]]
    [:And [:Comp [:Attribute "created"] [:RelOp ">="] [:DateValue "2022-03-29T14:21:37.659Z"]]]]])

(deftest check-parse-cimi-filter-transform-complex-query
  (is (= expected-transform-complex-query
         (t/parse-cimi-filter
           (str "(id=null or 'x'=id or d=1 and b=true) "
                "and name!=\"s\" and acl/owner='user/1' and attr intersects 'POINT(1 2)' "
                "or created>=2022-03-29T14:21:37.659Z")))))


(deftest check-valid-wkt-filter
  (are [expected v] (= expected (t/parse-cimi-filter v))
                    [:Filter
                     [:Or
                      [:And
                       [:Comp
                        [:Attribute "attr"]
                        [:GeoOp "intersects"]
                        [:WktValue [:StringValue "'POINT(1 2)'"]]]]]]
                    "attr intersects 'POINT(1 2)'"

                    [:Filter
                     [:Or
                      [:And
                       [:Comp
                        [:Attribute "attr"]
                        [:GeoOp "within"]
                        [:WktValue [:StringValue "\"POINT(1 2)\""]]]]]]
                    "attr within \"POINT(1 2)\""

                    [:Filter
                     [:Or
                      [:And
                       [:Comp
                        [:Attribute "attr"]
                        [:GeoOp "disjoint"]
                        [:WktValue [:StringValue "'any string is ok'"]]]]]]
                    "attr disjoint 'any string is ok'"))

(defn build-filter
  [n op]
  (str/join op (repeat n "(a='1')")))

(deftest test-parse-performance
  (is (t/parse-cimi-filter (build-filter 4000 " or ")))
  (is (t/parse-cimi-filter (build-filter 4000 " and "))))
