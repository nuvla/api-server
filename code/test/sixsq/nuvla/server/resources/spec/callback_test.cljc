(ns sixsq.nuvla.server.resources.spec.callback-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.callback :as t]
    [sixsq.nuvla.server.resources.spec.callback :as callback]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners   ["group/nuvla-admin"]
   :edit-acl ["group/nuvla-admin"]})


(deftest check-callback-schema
  (let [timestamp "1964-08-25T10:00:00Z"
        callback  {:id              (str t/resource-type "/test-callback")
                   :resource-type   t/resource-type
                   :created         timestamp
                   :updated         timestamp
                   :acl             valid-acl
                   :action          "validate-something"
                   :state           "WAITING"
                   :target-resource {:href "email/1230958abdef"}
                   :expires         timestamp
                   :tries-left      3
                   :data            {:some    "value"
                                     :another "value"}}]

    (stu/is-valid ::callback/schema callback)
    (stu/is-valid ::callback/schema (assoc callback :state "SUCCEEDED"))
    (stu/is-valid ::callback/schema (assoc callback :state "FAILED"))
    (stu/is-invalid ::callback/schema (assoc callback :state "UNKNOWN"))

    (doseq [attr #{:id :resource-type :created :updated :acl :action :state}]
      (stu/is-invalid ::callback/schema (dissoc callback attr)))

    (doseq [attr #{:target-resource :expires :data :tries-left}]
      (stu/is-valid ::callback/schema (dissoc callback attr)))))
