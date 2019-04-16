(ns sixsq.nuvla.server.resources.spec.data-record-key-prefix-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as key-prefix-record]
    [sixsq.nuvla.server.resources.spec.data-record-key-prefix :as key-prefix]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def timestamp "1970-04-16T08:40:00.00Z")


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(def valid-namespace
  {:acl           valid-acl
   :id            (str key-prefix-record/resource-type "/uuid")
   :prefix        "schema-org"
   :uri           "https://schema.org/schema1"
   :updated       timestamp
   :created       timestamp
   :resource-type key-prefix-record/resource-type})


(deftest check-prefix

  (doseq [k #{""
              " prefix "
              "not%allowed"
              "not.allowed"
              "not/allowed"
              "BAD"
              "-bad"
              "bad-"
              "0bad"}]
    (stu/is-invalid ::key-prefix/prefix k))

  (doseq [k #{"a"
              "a1"
              "alpha"
              "alpha-beta"
              "alpha1"}]
    (stu/is-valid ::key-prefix/prefix k)))


(deftest check-service-namespace

  (stu/is-valid ::key-prefix/schema valid-namespace)

  (stu/is-invalid ::key-prefix/schema (assoc valid-namespace :uri {:href ""}))
  (stu/is-invalid ::key-prefix/schema (assoc valid-namespace :uri {}))
  (stu/is-invalid ::key-prefix/schema (assoc valid-namespace :uri ""))
  (stu/is-invalid ::key-prefix/schema (assoc valid-namespace :prefix ""))

  (doseq [k #{:id :resource-type :created :updated :acl :prefix :uri}]
    (stu/is-invalid ::key-prefix/schema (dissoc valid-namespace k))))
