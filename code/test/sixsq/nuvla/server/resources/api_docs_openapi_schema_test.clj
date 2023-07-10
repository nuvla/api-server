(ns sixsq.nuvla.server.resources.api-docs-openapi-schema-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest is testing]]
    [sixsq.nuvla.server.resources.api-docs.openapi-schema :as t]
    [spec-tools.core :as st]
    [spec-tools.data-spec :as ds]
    [spec-tools.spec :as spec]))


;; Adapted from sixsq.nuvla.db.es.common.es-mapping-test


(s/def ::integer integer?)


(s/def ::string string?)


(s/def ::set-long #{1 2 3})


(s/def ::set-number #{1.0 2.0 3.0})                         ;; all non-integer numbers are promoted to doubles


(s/def ::set-string #{"a" "b" "c"})


(s/def ::a string?)


(s/def ::b string?)


(s/def ::c string?)


(s/def ::d string?)


(s/def ::e string?)


(s/def ::keys (s/keys :opt [::e]
                      :opt-un [::e]
                      :req [::a (or ::b (and ::c ::d))]
                      :req-un [::a (or ::b (and ::c ::d))]))


(s/def ::keys-no-req (s/keys :opt [::e]
                             :opt-un [::e]))


(deftest simple-spec-test
  (testing "primitive predicates"
    (is (= (t/transform (s/spec int?)) {:type "integer", :format "int64"}))
    (is (= (t/transform (s/spec integer?)) {:type "integer"}))
    (is (= (t/transform (s/spec float?)) {:type "number"}))
    (is (= (t/transform (s/spec double?)) {:type "number"}))
    (is (= (t/transform (s/spec string?)) {:type "string"}))
    (is (= (t/transform (s/spec boolean?)) {:type "boolean"}))
    (is (= (t/transform (s/spec decimal?)) {:type "number", :format "double"}))
    (is (= (t/transform (s/spec inst?)) {:type "string", :format "date-time"}))
    (is (= (t/transform (s/spec nil?)) {:type "null"}))

    (is (= (t/transform #{1 2 3}) {:type "integer"})))


  (testing "clojure.spec predicates"
    (is (= (t/transform (s/nilable ::string)) {:type "string"}))
    (is (= (t/transform (s/int-in 1 10)) {:type "integer", :format "int64"})))
  (testing "simple specs"
    (is (= (t/transform ::integer) {:type "integer"}))
    (is (= (t/transform ::set-long) {:type "integer"}))
    (is (= (t/transform ::set-number) {:type "number", :format "double"}))
    (is (= (t/transform ::set-string) {:type "string"})))

  (testing "clojure.specs"
    (is (= (t/transform (s/keys :req-un [::integer] :opt-un [::string]))
           {:type       "object"
            :properties {"integer" {:type "integer"} "string" {:type "string"}}}))
    (is (= (t/transform ::keys)
           {:type       "object"
            :title      "keys"
            :properties {"sixsq.nuvla.server.resources.api-docs-openapi-schema-test/a" {:type "string"}
                         "sixsq.nuvla.server.resources.api-docs-openapi-schema-test/b" {:type "string"}
                         "sixsq.nuvla.server.resources.api-docs-openapi-schema-test/c" {:type "string"}
                         "sixsq.nuvla.server.resources.api-docs-openapi-schema-test/d" {:type "string"}
                         "sixsq.nuvla.server.resources.api-docs-openapi-schema-test/e" {:type "string"}
                         "a"                                                           {:type "string"}
                         "b"                                                           {:type "string"}
                         "c"                                                           {:type "string"}
                         "d"                                                           {:type "string"}
                         "e"                                                           {:type "string"}}}))
    (is (= (t/transform ::keys-no-req)
           {:type       "object"
            :title      "keys-no-req"
            :properties {"sixsq.nuvla.server.resources.api-docs-openapi-schema-test/e" {:type "string"}
                         "e"                                                           {:type "string"}}}))
    ;; NOTE: ES mappings cannot have more than one type.  Ensure type matches one of the defined values.
    (let [multiple-types (t/transform (s/or :int integer? :string string?))]
      (is (or (= multiple-types {:type "integer"})
              (= multiple-types {:type "string"}))))
    (is (= (t/transform (s/and integer? pos?))
           {:type "integer"}))
    (is (= (t/transform (s/and spec/integer? pos?))
           {:type "integer"}))
    (is (= (t/transform (s/merge (s/keys :req [::integer])
                                 (s/keys :req [::string])))
           {:type       "object"
            :properties {"sixsq.nuvla.server.resources.api-docs-openapi-schema-test/integer" {:type "integer"}
                         "sixsq.nuvla.server.resources.api-docs-openapi-schema-test/string"  {:type "string"}}}))
    (is (= (t/transform (s/every integer?)) {:type "integer"}))
    (is (= (t/transform (s/every-kv string? integer?)) {:type "object"}))
    (is (= (t/transform (s/coll-of string?)) {:type "string"}))
    (is (= (t/transform (s/coll-of string? :into '())) {:type "string"}))
    (is (= (t/transform (s/coll-of string? :into [])) {:type "string"}))
    (is (= (t/transform (s/coll-of string? :into #{})) {:type "string"}))
    (is (= (t/transform (s/map-of string? integer?)) {:type "object"}))
    (is (= (t/transform (s/* integer?)) {:type "integer"}))
    (is (= (t/transform (s/+ integer?)) {:type "integer"}))
    (is (= (t/transform (s/? integer?)) {:type "integer"}))
    (is (= (t/transform (s/alt :int integer? :string string?)) {:type "integer"}))
    (is (= (t/transform (s/cat :int integer? :string string?)) {:type "integer"}))
    ;; & is broken (http://dev.clojure.org/jira/browse/CLJ-2152)
    ;; NOTE: This cannot be handled with ES mappings.  Just ensure no exception is thrown.
    (is (t/transform (s/tuple integer? string?)))
    ;; keys* is broken (http://dev.clojure.org/jira/browse/CLJ-2152)
    (is (= (t/transform (s/map-of string? clojure.core/integer?)) {:type "object"}))
    (is (= (t/transform (s/nilable string?)) {:type "string"})))
  (testing "failing clojure.specs"
    (is (not= (t/transform (s/coll-of (s/tuple string? any?) :into {}))
              {:type "object", :properties {:type "string"}}))))

;; Test the example from README

(s/def ::age (s/and integer? #(> % 18)))


(def person-spec
  (ds/spec
    ::person
    {::id                integer?
     :age                ::age
     :name               string?
     :likes              {string? boolean?}
     (ds/req :languages) #{keyword?}
     (ds/opt :address)   {:street string?
                          :zip    string?}}))

(deftest readme-test
  (is (= {:type  "object"
          :title "person"
          :properties
          {"sixsq.nuvla.server.resources.api-docs-openapi-schema-test/id" {:type "integer"}
           "age"                                                          {:type "integer"}
           "name"                                                         {:type "string"}
           "likes"                                                        {:type "object"}
           "languages"                                                    {:type "string"}
           "address"                                                      {:type       "object"
                                                                           :properties {"street" {:type "string"}
                                                                                        "zip"    {:type "string"}}}}}
         (t/transform person-spec))))


(deftest additional-json-schema-data-test
  (is (= {:type        "integer"
          :description "it's an int"
          :title       "integer"}
         (t/transform
           (st/spec
             {:spec                integer?
              :name                "integer"
              :description         "it's an int"
              :json-schema/default 42})))))


(deftest deeply-nested-test
  (is (= {:type "string"}
         (t/transform
           (ds/spec
             ::nested
             [[[[string?]]]])))))

(s/def ::user any?)
(s/def ::name string?)
(s/def ::parent (s/nilable ::user))
(s/def ::user (s/keys :req-un [::name ::parent]))


(deftest recursive-spec-test
  (is (= {:type       "object",
          :title      "user"
          :properties {"name"   {:type "string"}
                       "parent" {}}}
         (t/transform ::user))))
