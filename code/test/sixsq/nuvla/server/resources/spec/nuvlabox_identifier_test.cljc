(ns sixsq.nuvla.server.resources.spec.nuvlabox-identifier-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.nuvlabox-identifier :as t]
    [sixsq.nuvla.server.resources.spec.nuvlabox-identifier :as spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(deftest check-session-schema
  (let [timestamp "1964-08-25T10:00:00Z"
        cfg {:id            (str t/resource-type "/hash-of-identifier")
             :resource-type t/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :identifier    "some-long-identifier"
             :series        "exoplanets"
             :nuvlabox      {:href "nuvlabox-record/123456789"}}]

    (stu/is-valid ::spec/nuvlabox-identifier cfg)
    (stu/is-invalid ::spec/nuvlabox-identifier (assoc cfg :bad-attr "BAD_ATTR"))

    (doseq [attr #{:id :resource-type :created :updated :acl :identifier :series}]
      (stu/is-invalid ::spec/nuvlabox-identifier (dissoc cfg attr)))

    (doseq [attr #{:nuvlabox}]
      (stu/is-valid ::spec/nuvlabox-identifier (dissoc cfg attr)))))
