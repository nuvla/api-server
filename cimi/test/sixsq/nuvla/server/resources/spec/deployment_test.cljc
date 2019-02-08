(ns sixsq.nuvla.server.resources.spec.deployment-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.deployment :as d]
    [sixsq.nuvla.server.resources.spec.deployment :as ds]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})

(def timestamp "1964-08-25T10:00:00.0Z")


(def valid-module {:id          (str d/resource-type "/connector-uuid")
                   :resource-type d/resource-uri
                   :created     timestamp
                   :updated     timestamp
                   :acl         valid-acl

                   :module      {:href "my-module-uuid"}

                   :nodes       [{:nodeID     "my-node-uuid"
                                  :credential {:href "my-cred-uuid"}
                                  :cpu        10
                                  :ram        20
                                  :disk       30}
                                 {:nodeID     "my-second-node-uuid"
                                  :credential {:href "my-second-cred-uuid"}
                                  :cpu        100
                                  :ram        200
                                  :disk       300}]})


(def valid-deployment {:id               (str d/resource-type "/connector-uuid")
                       :resource-type      d/resource-uri
                       :created          timestamp
                       :updated          timestamp
                       :acl              valid-acl

                       :state            "STARTED"

                       :clientAPIKey     {:href   "credential/uuid"
                                          :secret "api secret"}

                       :template         {:href "deployment-template/uuid-1"}

                       :sshPublicKeys    ["ssh-rsa key1..." "ssh-rsa key2..."]

                       :outputParameters [{:parameter "param-1"}]
                       :module           (merge {:href "my-module-uuid"} valid-module)

                       :externalObjects  ["external-object/uuid1" "external-object/uuid2"]
                       :serviceOffers    {:service-offer/uuid1 ["service-offer/dataset1" "service-offer/dataset2"]
                                          :service-offer/uuid2 nil
                                          :service-offer/uuid3 ["service-offer/dataset3"]}})


(deftest test-schema-check
  (stu/is-valid ::ds/deployment valid-deployment)
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :badKey "badValue"))
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :module "must-be-href"))
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :sshPublicKeys "must-be-vector"))

  (stu/is-invalid ::ds/deployment (assoc valid-deployment :externalObjects ["BAD_ID"]))
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :serviceOffers {"BAD_ID" nil}))

  ;; required attributes
  (doseq [k #{:id :resource-type :created :updated :acl :state :module}]
    (stu/is-invalid ::ds/deployment (dissoc valid-deployment k)))

  ;; optional attributes
  (doseq [k #{:template :sshPublicKeys :externalObjects :serviceOffers}]
    (stu/is-valid ::ds/deployment (dissoc valid-deployment k))))
