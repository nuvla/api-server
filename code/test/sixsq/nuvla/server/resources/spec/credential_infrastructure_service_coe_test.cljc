(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service-coe-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-coe :as cred-infra-service-coe]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(deftest check-credential-service-docker
  (let [timestamp "1964-08-25T10:00:00Z"
        tpl       {:id            (str cred/resource-type "/uuid")
                   :resource-type cred/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :subtype       "swarm"
                   :method        "swarm"

                   :parent        "infrastructure-service/service-1"

                   :ca            "ca-public-certificate"
                   :cert          "client-public-certificate"
                   :key           "client-private-certificate"

                   :status        "VALID"
                   :last-check    timestamp}]

    (stu/is-valid ::cred-infra-service-coe/schema tpl)

    ;; mandatory keywords
    (doseq [k (-> tpl (dissoc :status :last-check) keys set)]
      (stu/is-invalid ::cred-infra-service-coe/schema (dissoc tpl k)))

    ;; optional keywords
    (doseq [k #{:last-check :status}]
      (stu/is-valid ::cred-infra-service-coe/schema (dissoc tpl k)))))
