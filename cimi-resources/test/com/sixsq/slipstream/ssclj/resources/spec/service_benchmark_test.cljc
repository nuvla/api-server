(ns com.sixsq.slipstream.ssclj.resources.spec.service-benchmark-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.slipstream.ssclj.resources.service-benchmark :as sb-resource]
    [com.sixsq.slipstream.ssclj.resources.spec.service-benchmark :as sb]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})

(def timestamp "1964-08-25T10:00:00.0Z")


(def service-bmk {:id            (str sb-resource/resource-url "/uuid")
                  :resourceURI   sb-resource/resource-uri
                  :created       timestamp
                  :updated       timestamp
                  :acl           valid-acl

                  :name          "bmk name"
                  :description   "short description",
                  :properties    {:a "one",
                                  :b "two"}

                  :credentials   [{:href "credential/e3db10f4-ad81-4b3e-8c04-4994450da9e3"}]

                  :serviceOffer  {:href                  "service-offer/e3db10f4-ad81-4b3e-8c04-4994450da9e3"
                                  :resource:vcpu         1
                                  :resource:ram          4096
                                  :resource:disk         10
                                  :resource:instanceType "Large"
                                  :connector             {
                                                          :href "connector/0123-4567-8912"
                                                          }}

                  :bmkname:score 3147.12

                  :other         "value"})


(deftest check-ServiceInfo

  (stu/is-valid ::sb/service-benchmark service-bmk)

  ;; mandatory keywords
  (doseq [k #{:created :updated :acl :credentials :serviceOffer}]
    (stu/is-invalid ::sb/service-benchmark (dissoc service-bmk k)))

  ;; optional keywords
  (doseq [k #{:bmkname:score :other}]
    (stu/is-valid ::sb/service-benchmark (dissoc service-bmk k))))
