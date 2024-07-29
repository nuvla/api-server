(ns com.sixsq.nuvla.server.resources.spec.deployment-parameter-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.module :as t]
    [com.sixsq.nuvla.server.resources.spec.deployment-parameter :as dp]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root      {:id            (str t/resource-type "/connector-uuid")
                   :resource-type t/resource-type
                   :parent        "deployment/7b1ad037-a65e-41e0-8fdf-e0e8db30bb0b"
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :node-id       "node-uuid"
                   :name          "my-parameter"
                   :value         "my-parameter-value"}]

    (stu/is-valid ::dp/deployment-parameter root)
    (stu/is-invalid ::dp/deployment-parameter (assoc root :badKey "badValue"))
    (stu/is-invalid ::dp/deployment-parameter (assoc root :value "   "))

    ;; required attributes
    (doseq [k #{:id :resource-type :parent :created :updated :acl :name}]
      (stu/is-invalid ::dp/deployment-parameter (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:value :node-id}]
      (stu/is-valid ::dp/deployment-parameter (dissoc root k)))))
