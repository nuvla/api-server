(ns com.sixsq.nuvla.server.resources.spec.data-set-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.data-set :as t]
    [com.sixsq.nuvla.server.resources.spec.data-set :as data-set]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-email-schema
  (let [timestamp "1964-08-25T10:00:00.00Z"
        data-set  {:id                 (str t/resource-type "/abcdef")
                   :name               "my great data set"
                   :description        "collects all of my favorite data"
                   :resource-type      t/resource-type
                   :created            timestamp
                   :updated            timestamp
                   :acl                valid-acl

                   :module-filter      "(filter='module')"
                   :data-object-filter "(filter='object')"
                   :data-record-filter "(filter='record')"}]

    (stu/is-valid ::data-set/schema data-set)

    (stu/is-invalid ::data-set/schema (assoc data-set :bad "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::data-set/schema (dissoc data-set attr)))

    (doseq [attr #{:name :description :module-filter :data-object-filter :data-record-filter}]
      (stu/is-valid ::data-set/schema (dissoc data-set attr)))))
