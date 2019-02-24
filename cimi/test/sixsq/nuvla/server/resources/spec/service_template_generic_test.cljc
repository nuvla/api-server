(ns sixsq.nuvla.server.resources.spec.service-template-generic-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.service-template :as tpl]
    [sixsq.nuvla.server.resources.service-template-generic :as tpl-generic]
    [sixsq.nuvla.server.resources.spec.service-template-generic :as spec-generic]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest check-service-template-generic-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id            (str tpl/resource-type "/generic")
             :resource-type tpl/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :method        tpl-generic/method

             :type          "s3"
             :endpoint      "https://s3.example.org:2000"
             :accessible    true}]

    (stu/is-valid ::spec-generic/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :type :endpoint}]
      (stu/is-invalid ::spec-generic/schema (dissoc cfg attr)))

    (doseq [attr #{:accessible}]
      (stu/is-valid ::spec-generic/schema (dissoc cfg attr)))))


























