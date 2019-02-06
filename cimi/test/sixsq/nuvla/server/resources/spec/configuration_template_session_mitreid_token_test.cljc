(ns sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid-token-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid-token :as cts-mitreid-token]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id          (str ct/resource-url "/session-mitreid-token-test-instance")
              :resourceURI p/service-context
              :created     timestamp
              :updated     timestamp
              :acl         valid-acl

              :service     "session-mitreid-token"
              :instance    "test-instance"
              :clientIPs   ["127.0.0.1", "192.168.100.100"]}]

    (stu/is-valid ::cts-mitreid-token/schema root)

    (stu/is-valid ::cts-mitreid-token/schema (assoc root :clientIPs ["127.0.0.1"]))
    (stu/is-invalid ::cts-mitreid-token/schema (assoc root :clientIPs "127.0.0.1"))

    (doseq [k #{:id :resourceURI :created :updated :acl :service :instance}]
      (stu/is-invalid ::cts-mitreid-token/schema (dissoc root k)))

    (doseq [k #{:clientIPs}]
      (stu/is-valid ::cts-mitreid-token/schema (dissoc root k)))))
