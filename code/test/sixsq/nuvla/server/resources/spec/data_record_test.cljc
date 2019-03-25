(ns sixsq.nuvla.server.resources.spec.data-record-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.data-record :as data-record-resource]
    [sixsq.nuvla.server.resources.spec.data-record :as data-record]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest check-data-record
  (let [timestamp "1964-08-25T10:00:00.0Z"
        data-record {:id                     (str data-record-resource/resource-type "/uuid")
                     :resource-type          data-record-resource/resource-type
                     :created                timestamp
                     :updated                timestamp
                     :acl                    valid-acl
                     :infrastructure-service "infrastructure-service/my-service-uuid"
                     :other                  "value"}]

    (stu/is-valid ::data-record/schema data-record)

    ;; mandatory keywords
    (doseq [k #{:created :updated :acl :infrastructure-service}]
      (stu/is-invalid ::data-record/schema (dissoc data-record k)))

    ;; optional keywords
    (doseq [k #{:other}]
      (stu/is-valid ::data-record/schema (dissoc data-record k)))))
