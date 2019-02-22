(ns sixsq.nuvla.server.resources.spec.credential-template-driver-exoscale-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-driver-exoscale :as driver]
    [sixsq.nuvla.server.resources.spec.credential-template-driver-exoscale :as driver-spec]))


(def valid-acl driver/resource-acl-default)


(deftest test-credential-template-driver-exoscale-create-schema-check
  (let [root {:resource-type p/resource-type
              :template      {:exoscale-api-key           "foo"
                              :exoscale-api-secret-key    "bar"
                              }}]
    (is (s/valid? ::driver-spec/schema-create root))
    (doseq [k (into #{} (keys (dissoc root :resource-type)))]
      (is (not (s/valid? ::driver-spec/schema-create (dissoc root k)))))))


(deftest test-credential-template-driver-exoscale-schema-check
  (let [timestamp "1972-10-08T10:00:00.0Z"
        root {:id            (str ct/resource-type "/uuid")
              :resource-type p/resource-type
              :created       timestamp
              :updated       timestamp
              :acl           valid-acl
              :type          driver/cred-type
              :method        driver/cred-method
              :exoscale-api-key           "foo"
              :exoscale-api-secret-key    "bar"
              }]
    (is (s/valid? ::driver-spec/schema root))
    (doseq [k (into #{} (keys root))]
      (is (not (s/valid? ::driver-spec/schema (dissoc root k)))))))
