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


(def valid-deployment {:id               (str d/resource-type "/connector-uuid")
                       :resource-type    d/resource-type
                       :created          timestamp
                       :updated          timestamp
                       :acl              valid-acl

                       :state            "STARTED"

                       :api-credentials  {:api-key    "credential/uuid"
                                          :api-secret "api secret"}

                       :credential-id    "credential/my-cloud-credential"

                       :module           {:href "module-image/my-module-image-uuid"}

                       :external-objects ["external-object/uuid1" "external-object/uuid2"]
                       :data-records     {:data-record/uuid1 ["data-record/dataset1" "data-record/dataset2"]
                                          :data-record/uuid2 nil
                                          :data-record/uuid3 ["data-record/dataset3"]}})


(deftest test-schema-check
  (stu/is-valid ::ds/deployment valid-deployment)
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :badKey "badValue"))
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :module "must-be-href"))

  (stu/is-invalid ::ds/deployment (assoc valid-deployment :external-objects ["BAD_ID"]))
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :data-records {"BAD_ID" nil}))

  ;; required attributes
  (doseq [k #{:id :resource-type :created :updated :acl :state :module :credential-id}]
    (stu/is-invalid ::ds/deployment (dissoc valid-deployment k)))

  ;; optional attributes
  (doseq [k #{:external-objects :data-records}]
    (stu/is-valid ::ds/deployment (dissoc valid-deployment k))))
