(ns sixsq.nuvla.server.resources.spec.credential-service-docker-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-service-swarm :as cred-docker]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "jane"
                         :type      "USER"
                         :right     "VIEW"}]})


(deftest check-credential-service-docker
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cred {:id            (str cred/resource-type "/uuid")
              :resource-type cred/resource-type
              :created       timestamp
              :updated       timestamp
              :acl           valid-acl

              :type          "swarm"
              :method        "swarm"

              :services      [{:href "service/service-1"}
                              {:href "service/service-2"}]

              :ca            "ca-public-certificate"
              :cert          "client-public-certificate"
              :key           "client-private-certificate"}]

    (stu/is-valid ::cred-docker/schema cred)

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl :services :ca :cert :key}]
      (stu/is-invalid ::cred-docker/schema (dissoc cred k)))))
