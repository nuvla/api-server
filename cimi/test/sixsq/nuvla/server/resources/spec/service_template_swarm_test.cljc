(ns sixsq.nuvla.server.resources.spec.service-template-swarm-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.service-template :as tpl]
    [sixsq.nuvla.server.resources.service-template-swarm :as tpl-swarm]
    [sixsq.nuvla.server.resources.spec.service-template-swarm :as spec-swarm]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest check-service-template-create-swarm-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id                 (str tpl/resource-type "/swarm")
             :resource-type      tpl/resource-type
             :created            timestamp
             :updated            timestamp
             :acl                valid-acl

             :method             tpl-swarm/method
             :type               tpl-swarm/method

             :cloud-service      {:href "service/my-cloud-service"}
             :service-credential {:href "credential/my-cloud-credential"}}]

    (stu/is-valid ::spec-swarm/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :cloud-service :service-credential}]
      (stu/is-invalid ::spec-swarm/schema (dissoc cfg attr)))))


























