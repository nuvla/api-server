(ns sixsq.nuvla.server.resources.spec.data-record-key-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.data-record-key :as data-record-key-resource]
    [sixsq.nuvla.server.resources.spec.data-record-key :as data-record-key]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest check-attribute
  (let [timestamp "1964-08-25T10:00:00.00Z"
        attr {:id            (str data-record-key-resource/resource-type "/test-attribute")
              :name          "Test Attribute"
              :description   "A attribute containing a test value."
              :resource-type data-record-key-resource/resource-type
              :created       timestamp
              :updated       timestamp
              :acl           valid-acl

              :prefix        "example-org"
              :key           "test-key"
              :subtype       "string"}]


    (stu/is-valid ::data-record-key/schema attr)

    (stu/is-invalid ::data-record-key/schema (assoc attr :prefix 0))
    (stu/is-invalid ::data-record-key/schema (assoc attr :prefix ""))

    (stu/is-invalid ::data-record-key/schema (assoc attr :key 0))
    (stu/is-invalid ::data-record-key/schema (assoc attr :key ""))

    (stu/is-valid ::data-record-key/schema (assoc attr :subtype "string"))

    ;; mandatory keywords
    (doseq [k #{:id :name :description :created :updated :acl :prefix :key :subtype}]
      (stu/is-invalid ::data-record-key/schema (dissoc attr k)))))
