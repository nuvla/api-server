(ns sixsq.nuvla.server.resources.spec.credential-hashed-password-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-hashed-password :as hashed-pwd]
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

             :hash          "some-hash-of-a-password"}]

    (stu/is-valid ::hashed-pwd/schema tpl)

    ;; mandatory keywords
    (doseq [k (-> tpl (dissoc :services) keys set)]
      (stu/is-invalid ::hashed-pwd/schema (dissoc tpl k)))

    ;; optional keywords
    (doseq [k #{:services}]
      (stu/is-valid ::hashed-pwd/schema (dissoc tpl k)))))
