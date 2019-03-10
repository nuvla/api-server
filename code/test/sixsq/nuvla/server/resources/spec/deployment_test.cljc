(ns sixsq.nuvla.server.resources.spec.deployment-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.deployment :as d]
    [sixsq.nuvla.server.resources.spec.deployment :as ds]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})

(def timestamp "1964-08-25T10:00:00.0Z")


(def valid-deployment {:id                        (str d/resource-type "/connector-uuid")
                       :resource-type             d/resource-type
                       :created                   timestamp
                       :updated                   timestamp
                       :acl                       valid-acl

                       :state                     "STARTED"

                       :api-credentials           {:api-key    "credential/uuid"
                                                   :api-secret "api secret"}

                       :credential-id             "credential/my-cloud-credential"
                       :infrastructure-service-id "infrastructure-service/my-service"

                       :module                    {:href "module-component/my-module-component-uuid"}

                       :data-objects              ["data-object/uuid1" "data-object/uuid2"]
                       :data-records              {:data-record/uuid1 ["data-set/dataset1" "data-set/dataset2"]
                                                   :data-record/uuid2 nil
                                                   :data-record/uuid3 ["data-set/dataset3"]}})


(deftest test-schema-check
  (stu/is-valid ::ds/deployment valid-deployment)
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :badKey "badValue"))
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :module "must-be-href"))

  (stu/is-invalid ::ds/deployment (assoc valid-deployment :data-objects ["BAD_ID"]))
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :data-records {"BAD_ID" nil}))

  ;; required attributes
  (doseq [k #{:id :resource-type :created :updated :acl :state :module}]
    (stu/is-invalid ::ds/deployment (dissoc valid-deployment k)))

  ;; optional attributes
  (doseq [k #{:data-objects :data-records :credential-id :infrastructure-service-id}]
    (stu/is-valid ::ds/deployment (dissoc valid-deployment k))))
