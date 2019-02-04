(ns com.sixsq.slipstream.ssclj.resources.spec.service-attribute-namespace-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.slipstream.ssclj.resources.service-attribute-namespace :as sn]
    [com.sixsq.slipstream.ssclj.resources.spec.service-attribute-namespace :as san]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


(def timestamp "1970-04-16T08:40:00.0Z")


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(def valid-namespace
  {:acl         valid-acl
   :id          (str sn/resource-url "/uuid")
   :prefix      "schema-org"
   :uri         "https://schema.org/schema1"
   :updated     timestamp
   :created     timestamp
   :resourceURI sn/resource-uri})


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
    (stu/is-invalid ::san/prefix k))

  (doseq [k #{"a"
              "a1"
              "alpha"
              "alpha-beta"
              "alpha1"}]
    (stu/is-valid ::san/prefix k)))


(deftest check-service-namespace

  (stu/is-valid ::san/service-attribute-namespace valid-namespace)

  (stu/is-invalid ::san/service-attribute-namespace (assoc valid-namespace :uri {:href ""}))
  (stu/is-invalid ::san/service-attribute-namespace (assoc valid-namespace :uri {}))
  (stu/is-invalid ::san/service-attribute-namespace (assoc valid-namespace :uri ""))
  (stu/is-invalid ::san/service-attribute-namespace (assoc valid-namespace :prefix ""))

  (doseq [k #{:id :resourceURI :created :updated :acl :prefix :uri}]
    (stu/is-invalid ::san/service-attribute-namespace (dissoc valid-namespace k))))
