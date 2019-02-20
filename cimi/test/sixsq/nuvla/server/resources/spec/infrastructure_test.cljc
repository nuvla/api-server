(ns sixsq.nuvla.server.resources.spec.infrastructure-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.infrastructure :as infra]
    [sixsq.nuvla.server.resources.spec.infrastructure-nuvla :as infra-nuvla]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest check-infrastructure-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id            (str infra/resource-type "/uuid")
             :resource-type infra/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :state         "CREATED"
             :endpoint      "https://swarm.example.org/endpoint"
             :tls-key       "my-key"
             :tls-cert      "my-cert"
             :tls-ca        "my-ca"}]

    (stu/is-valid ::infra-nuvla/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :state}]
      (stu/is-invalid ::infra-nuvla/schema (dissoc cfg attr)))

    (doseq [attr #{:endpoint :tls-key :tls-cert :tls-ca}]
      (stu/is-valid ::infra-nuvla/schema (dissoc cfg attr)))))
