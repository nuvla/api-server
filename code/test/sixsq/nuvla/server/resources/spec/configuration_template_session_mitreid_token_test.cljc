(ns sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid-token-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid-token :as cts-mitreid-token]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners    ["group/nuvla-admin"]
                :view-data ["group/nuvla-anon"]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00Z"
        root      {:id            (str ct/resource-type "/session-mitreid-token-test-instance")
                   :resource-type ct/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :service       "session-mitreid-token"
                   :instance      "test-instance"
                   :client-ips    ["127.0.0.1" "192.168.100.100"]}]

    (stu/is-valid ::cts-mitreid-token/schema root)

    (stu/is-valid ::cts-mitreid-token/schema (assoc root :client-ips ["127.0.0.1"]))
    (stu/is-invalid ::cts-mitreid-token/schema (assoc root :client-ips "127.0.0.1"))

    (doseq [k #{:id :resource-type :created :updated :acl :service :instance}]
      (stu/is-invalid ::cts-mitreid-token/schema (dissoc root k)))

    (doseq [k #{:client-ips}]
      (stu/is-valid ::cts-mitreid-token/schema (dissoc root k)))))
