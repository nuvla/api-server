(ns sixsq.nuvla.server.resources.spec.connector-template-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.connector-template :as ct]
    [sixsq.nuvla.server.resources.spec.connector-template :as cts]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def :cimi.test/connector-template (su/only-keys-maps cts/resource-keys-spec))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id                  (str ct/resource-url "/uuid")
              :resource-type         ct/resource-uri
              :created             timestamp
              :updated             timestamp
              :acl                 valid-acl
              :cloudServiceType    "cloud-software-solution"
              :orchestratorImageid "123"
              :quotaVm             "20"
              :maxIaasWorkers      5
              :instanceName        "foo"}]

    (stu/is-valid :cimi.test/connector-template root)
    (doseq [k (into #{} (keys (dissoc root :id :resource-type)))]
      (stu/is-invalid :cimi.test/connector-template (dissoc root k)))))
