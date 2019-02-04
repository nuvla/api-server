(ns com.sixsq.slipstream.ssclj.resources.spec.metering-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.slipstream.ssclj.resources.metering :as m]
    [com.sixsq.slipstream.ssclj.resources.spec.metering :as metering]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN",
                        :type      "ROLE"},
                :rules [{:principal "realm:accounting_manager",
                         :type      "ROLE",
                         :right     "VIEW"},
                        {:principal "test",
                         :type      "USER",
                         :right     "VIEW"},
                        {:principal "cern:cern",
                         :type      "ROLE",
                         :right     "VIEW"},
                        {:principal "cern:my-accounting-group",
                         :type      "ROLE",
                         :right     "VIEW"}]})


(def timestamp "1964-08-25T10:00:00.0Z")


(def metering-sample {:id            (str m/resource-url "/uuid")
                      :resourceURI   m/resource-uri
                      :created       timestamp
                      :updated       timestamp
                      :acl           valid-acl

                      :name          "short name"
                      :description   "short description",
                      :properties    {:a "one", :b "two"}

                      :instanceID    "aaa-bbb-111"
                      :connector     {:href "connector/0123-4567-8912"}
                      :state         "Running"
                      :billable      true
                      :ip            "127.0.0.1"
                      :credentials   [{:href  "credential/0123-4567-8912",
                                       :roles ["realm:cern", "realm:my-accounting-group"]
                                       :users ["long-user-id-1", "long-user-id-2"]}]
                      :deployment    {:href "run/aaa-bbb-ccc",
                                      :user {:href "user/test"}}
                      :serviceOffer  {:href                  "service-offer/e3db10f4-ad81-4b3e-8c04-4994450da9e3"
                                      :resource:vcpu         1
                                      :resource:ram          4096
                                      :resource:disk         10
                                      :resource:instanceType "Large"
                                      :price:unitCost        42
                                      :price:unitCode        "HUR"
                                      :price:currency        "EUR"}
                      :price         0.89883
                      :currency      "EUR"
                      :snapshot-time timestamp})


(deftest test-schema-check

  (stu/is-valid ::metering/metering metering-sample)

  (stu/is-invalid ::metering/metering (assoc metering-sample :bad-attr {}))
  (stu/is-invalid ::metering/metering (assoc metering-sample :bad-attr "test"))

  ;; mandatory keywords
  (doseq [k #{:credentials :state :instanceID :snapshot-time}]
    (stu/is-invalid ::metering/metering (dissoc metering-sample k)))

  ;; optional keywords
  (doseq [k #{:run :serviceOffer :ip :price :currency :billable}]
    (stu/is-valid ::metering/metering (dissoc metering-sample k))))
