(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service-minio-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-minio :as cred-infra-service-minio]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners   ["group/nuvla-admin"]
   :view-acl ["user/jane"]})


(deftest check-credential-service-minio
  (let [timestamp "1964-08-25T10:00:00.00Z"
        tpl {:id            (str cred/resource-type "/uuid")
             :resource-type cred/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :type          "minio"
             :method        "minio"

             :parent        "infrastructure-service/service-1"

             :access-key    "access-key"
             :secret-key    "secret-key"}]

    (stu/is-valid ::cred-infra-service-minio/schema tpl)

    ;; mandatory keywords
    (doseq [k (-> tpl keys set)]
      (stu/is-invalid ::cred-infra-service-minio/schema (dissoc tpl k)))

    ;; optional keywords
    (doseq [k #{}]
      (stu/is-valid ::cred-infra-service-minio/schema (dissoc tpl k)))))
