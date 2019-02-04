(ns com.sixsq.slipstream.ssclj.resources.spec.evidence-record-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [com.sixsq.slipstream.ssclj.resources.spec.evidence-record :as evspec]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

(s/def :cimi.test/evidence-record (su/only-keys-maps evspec/evidence-record-spec))

(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "USER"
                         :type      "ROLE"
                         :right     "VIEW"}]})

(def timestamp "1964-08-25T10:00:00.0Z")

(deftest evid
  (let [root {
              ; :id            (str ev/resource-url "/uuid")
              ; :resourceURI   ev/resource-uri
              ; :created       timestamp
              ; :updated       timestamp
              ; :acl           valid-acl

              ; :name          "name"
              ; :description   "short description",
              ; :properties    {:a "one",
              ;                 :b "two"}

              ; :credentials   [{:href "credential/e3db10f4-ad81-4b3e-8c04-4994450da9e3"}]

              :endTime   timestamp
              :startTime timestamp
              :planID    "b12345"
              :passed    true
              :class     "className"
              :log       ["log1", "log2"]}]

    (stu/is-valid :cimi.test/evidence-record root)

    ;; mandatory keywords
    (doseq [k #{:endTime :passed :planID :startTime}]
      (stu/is-invalid :cimi.test/evidence-record (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:log :class}]
      (stu/is-valid :cimi.test/evidence-record (dissoc root k)))))

