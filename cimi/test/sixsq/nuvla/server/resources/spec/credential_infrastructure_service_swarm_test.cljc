(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service-swarm-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-swarm :as cred-infra-service-swarm]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "jane"
                         :type      "USER"
                         :right     "VIEW"}]})


(deftest check-credential-service-docker
  (let [timestamp "1964-08-25T10:00:00.0Z"
        tpl {:id            (str cred/resource-type "/uuid")
             :resource-type cred/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :type          "swarm"
             :method        "swarm"

             :services      ["infrastructure-service/service-1"
                             "infrastructure-service/service-2"]

             :ca            "ca-public-certificate"
             :cert          "client-public-certificate"
             :key           "client-private-certificate"}]

    (stu/is-valid ::cred-infra-service-swarm/schema tpl)

    ;; mandatory keywords
    (doseq [k (-> tpl (dissoc :services) keys set)]
      (stu/is-invalid ::cred-infra-service-swarm/schema (dissoc tpl k)))

    ;; optional keywords
    (doseq [k #{:services}]
      (stu/is-valid ::cred-infra-service-swarm/schema (dissoc tpl k)))))
