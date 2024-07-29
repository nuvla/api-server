(ns com.sixsq.nuvla.server.resources.spec.nuvlabox-release-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.nuvlabox-release :as t]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-release :as nuvlabox-release]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(deftest check-nuvlabox-release-schema
  (let [timestamp        "1964-08-25T10:00:00.00Z"
        nuvlabox-release {:id            (str t/resource-type "/abcdef")
                          :name          "my nuvlabox-release"
                          :description   "description of my nuvlabox-release"
                          :resource-type t/resource-type
                          :created       timestamp
                          :updated       timestamp
                          :acl           valid-acl
                          :release       "1.0.0"
                          :release-date  "2020-01-28T13:48:03Z"
                          :release-notes "added this \n changed that \r\n"
                          :url           "https://github.com/nuvlabox/deployment/releases/tag/1.0.0"
                          :pre-release   false
                          :compose-files [{:file  "version: '3.7'\n\nservices:"
                                           :name  "docker-compose.yml"
                                           :scope "core"}]
                          :published     true}]

    (stu/is-valid ::nuvlabox-release/schema nuvlabox-release)

    (stu/is-invalid ::nuvlabox-release/schema (assoc nuvlabox-release :bad "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::nuvlabox-release/schema (dissoc nuvlabox-release attr)))

    (doseq [attr #{:name :description :published}]
      (stu/is-valid ::nuvlabox-release/schema (dissoc nuvlabox-release attr)))))
