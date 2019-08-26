(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-google-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-google :as service]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-google :as service-spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl service/resource-acl)


(deftest test-credential-template-service-schema-check
  (let [timestamp "1972-10-08T10:00:00.00Z"
        tpl       {:id             (str ct/resource-type "/uuid")
                   :resource-type  p/resource-type
                   :created        timestamp
                   :updated        timestamp
                   :acl            valid-acl
                   :subtype        service/credential-subtype
                   :method         service/method
                   :project-id     "my-project-id"
                   :private-key-id "abcde1234"
                   :private-key    "-----BEGIN PRIVATE KEY-----\\nMIIaA0n\\n-----END PRIVATE KEY-----\\n"
                   :client-email   "1234-compute@developer.gserviceaccount.com"
                   :client-id      "98765"
                   :parent         "infrastructure-service/service-1"}]

    (stu/is-valid ::service-spec/schema tpl)

    ;; mandatory keys
    (doseq [k (-> tpl (dissoc :parent) keys set)]
      (stu/is-invalid ::service-spec/schema (dissoc tpl k)))

    ;; optional keys
    (doseq [k #{:parent}]
      (stu/is-valid ::service-spec/schema (dissoc tpl k)))))






















