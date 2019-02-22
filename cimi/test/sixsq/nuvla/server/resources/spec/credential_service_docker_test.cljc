(ns sixsq.nuvla.server.resources.spec.credential-service-docker-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-service-docker :as cred-docker]
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

              :type          "docker"
              :method        "docker-static"

              :service       {:href "service/service-1"}

              :ca            "ca-public-certificate"
              :cert          "client-public-certificate"
              :key           "client-private-certificate"}]

    (stu/is-valid ::cred-docker/schema cred)

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl :ca :cert :key}]
      (stu/is-invalid ::cred-docker/schema (dissoc cred k)))))
