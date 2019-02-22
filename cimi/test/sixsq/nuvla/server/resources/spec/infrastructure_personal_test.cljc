(ns sixsq.nuvla.server.resources.spec.infrastructure-personal-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.infrastructure :as infra]
    [sixsq.nuvla.server.resources.spec.infrastructure-personal :as infra-personal]
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

             :type          "nuvla"
             :template      {:href "infrastructure-template/nuvla"}

             :state         "CREATED"
             :endpoint      "https://swarm.example.org/endpoint"
             :tls-key       "my-key"
             :tls-cert      "my-cert"
             :tls-ca        "my-ca"}]

    (stu/is-valid ::infra-personal/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :state :type :template}]
      (stu/is-invalid ::infra-personal/schema (dissoc cfg attr)))

    (doseq [attr #{:endpoint :tls-key :tls-cert :tls-ca}]
      (stu/is-valid ::infra-personal/schema (dissoc cfg attr)))))
