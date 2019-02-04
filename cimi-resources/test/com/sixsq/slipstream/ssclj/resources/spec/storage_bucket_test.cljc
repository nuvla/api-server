(ns com.sixsq.slipstream.ssclj.resources.spec.storage-bucket-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]
    [com.sixsq.slipstream.ssclj.resources.spec.storage-bucket :as bucky]
    [com.sixsq.slipstream.ssclj.resources.storage-bucket :as bucky-resource]))


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


(def bucky-sample {:id             (str bucky-resource/resource-url "/uuid")
                   :resourceURI    bucky-resource/resource-uri
                   :created        timestamp
                   :updated        timestamp
                   :acl            valid-acl
                   :name           "short name"
                   :description    "short description",
                   :properties     {:a "one", :b "two"}
                   :bucketName     "aaa-bbb-111"
                   :usageInKiB     123456
                   :connector      {:href "connector/0123-4567-8912"}
                   :credentials    [{:href "credentials/0123-4567-8912"}]
                   :externalObject {:href "external-object/aaa-bbb-ccc", :user {:href "user/test"}}
                   :serviceOffer   {:href              "service-offer/e3db10f4-ad81-4b3e-8c04-4994450da9e3"
                                    :resource:storage  1
                                    :resource:host     "s3-eu-west-1.amazonaws.com"
                                    :price:currency    "EUR"
                                    :price:unitCode    "HUR"
                                    :price:unitCost    "0.018"
                                    :resource:platform "S3"}
                   :currency       "EUR"})


(deftest test-schema-check

  (stu/is-valid ::bucky/storage-bucket bucky-sample)
  (stu/is-valid ::bucky/storage-bucket (assoc bucky-sample :externalObject {:href "external-object/fff-42"}))
  (stu/is-valid ::bucky/storage-bucket (assoc bucky-sample :name "name"))

  (stu/is-valid ::bucky/storage-bucket (assoc bucky-sample :credentials [{:href "credential/1234"}]))

  (stu/is-invalid ::bucky/storage-bucket (assoc bucky-sample :bad-attr {}))
  (stu/is-invalid ::bucky/storage-bucket (assoc bucky-sample :bad-attr "test"))

  ;; mandatory keywords
  (doseq [k #{:credentials :bucketName :connector :usageInKiB}]
    (stu/is-invalid ::bucky/storage-bucket (dissoc bucky-sample k)))

  ;; optional keywords
  (doseq [k #{:externalObject :serviceOffer :currency}]
    (stu/is-valid ::bucky/storage-bucket (dissoc bucky-sample k))))
