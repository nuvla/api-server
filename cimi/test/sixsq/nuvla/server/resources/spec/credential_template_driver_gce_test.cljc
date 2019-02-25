(ns sixsq.nuvla.server.resources.spec.credential-template-driver-gce-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-driver-gce :as driver]
    [sixsq.nuvla.server.resources.spec.credential-template-driver-gce :as driver-spec]))


(def valid-acl driver/resource-acl)


(deftest test-credential-template-driver-create-schema-check
  (let [root {:resource-type p/resource-type
              :template      {:project-id              "my-project-id"
                              :private-key-id          "abcde1234"
                              :private-key             "-----BEGIN PRIVATE KEY-----\\nMIIaA0n\\n-----END PRIVATE KEY-----\\n"
                              :client-email            "1234-compute@developer.gserviceaccount.com"
                              :client-id               "98765"
                              }}]
    (is (s/valid? ::driver-spec/schema-create root))
    (doseq [k (into #{} (keys (dissoc root :resource-type)))]
      (is (not (s/valid? ::driver-spec/schema-create (dissoc root k)))))))


(deftest test-credential-template-driver-schema-check
  (let [timestamp "1972-10-08T10:00:00.0Z"
        root {:id            (str ct/resource-type "/uuid")
              :resource-type p/resource-type
              :created       timestamp
              :updated       timestamp
              :acl           valid-acl
              :type          driver/credential-type
              :method        driver/method
              :project-id              "my-project-id"
              :private-key-id          "abcde1234"
              :private-key             "-----BEGIN PRIVATE KEY-----\\nMIIaA0n\\n-----END PRIVATE KEY-----\\n"
              :client-email            "1234-compute@developer.gserviceaccount.com"
              :client-id               "98765"
              }]
    (is (s/valid? ::driver-spec/schema root))
    (doseq [k (into #{} (keys root))]
      (is (not (s/valid? ::driver-spec/schema (dissoc root k)))))))
