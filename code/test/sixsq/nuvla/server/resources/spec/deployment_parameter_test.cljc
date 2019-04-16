(ns sixsq.nuvla.server.resources.spec.deployment-parameter-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.module :as t]
    [sixsq.nuvla.server.resources.spec.deployment-parameter :as dp]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root {:id            (str t/resource-type "/connector-uuid")
              :resource-type t/resource-type
              :created       timestamp
              :updated       timestamp
              :acl           valid-acl

              :deployment    {:href "deployment-uuid"}
              :node-id       "node-uuid"
              :name          "my-parameter"
              :value         "my-parameter-value"}]

    (stu/is-valid ::dp/deployment-parameter root)
    (stu/is-invalid ::dp/deployment-parameter (assoc root :badKey "badValue"))
    (stu/is-invalid ::dp/deployment-parameter (assoc root :value "   "))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :deployment :name}]
      (stu/is-invalid ::dp/deployment-parameter (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:values :node-id}]
      (stu/is-valid ::dp/deployment-parameter (dissoc root k)))))
