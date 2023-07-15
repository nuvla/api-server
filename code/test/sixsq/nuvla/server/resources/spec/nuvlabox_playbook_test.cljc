(ns sixsq.nuvla.server.resources.spec.nuvlabox-playbook-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.nuvlabox-playbook :as t]
    [sixsq.nuvla.server.resources.spec.nuvlabox-playbook :as nuvlabox-playbook]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(deftest check-nuvlabox-playbook-schema
  (let [timestamp         "1964-08-25T10:00:00Z"
        nuvlabox-playbook {:id            (str t/resource-type "/abcdef")
                           :name          "my nuvlabox-playbook"
                           :description   "description of my nuvlabox-playbook"
                           :resource-type t/resource-type
                           :created       timestamp
                           :updated       timestamp
                           :acl           valid-acl
                           :type          "EMERGENCY"
                           :run           "echo hello world"
                           :parent        "nuvlabox/123-abc"
                           :enabled       true
                           :output        "foo"}]

    (stu/is-valid ::nuvlabox-playbook/schema nuvlabox-playbook)

    (stu/is-invalid ::nuvlabox-playbook/schema (assoc nuvlabox-playbook :bad "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl :run :parent :enabled :type}]
      (stu/is-invalid ::nuvlabox-playbook/schema (dissoc nuvlabox-playbook attr)))

    (doseq [attr #{:name :description :output}]
      (stu/is-valid ::nuvlabox-playbook/schema (dissoc nuvlabox-playbook attr)))))
