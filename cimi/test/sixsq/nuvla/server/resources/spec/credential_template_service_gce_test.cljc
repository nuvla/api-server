(ns sixsq.nuvla.server.resources.spec.credential-template-service-gce-test
    (:require
      [clojure.spec.alpha :as s]
      [clojure.test :refer :all]
      [sixsq.nuvla.server.resources.credential :as p]
      [sixsq.nuvla.server.resources.credential-template :as ct]
      [sixsq.nuvla.server.resources.credential-template-service-gce :as service]
      [sixsq.nuvla.server.resources.spec.credential-template-service-gce :as service-spec]))


(def valid-acl service/resource-acl)


(deftest test-credential-template-service-schema-check
         (let [timestamp "1972-10-08T10:00:00.0Z"
               root {:id             (str ct/resource-type "/uuid")
                     :resource-type  p/resource-type
                     :created        timestamp
                     :updated        timestamp
                     :acl            valid-acl
                     :type           service/credential-type
                     :method         service/method
                     :project-id     "my-project-id"
                     :private-key-id "abcde1234"
                     :private-key    "-----BEGIN PRIVATE KEY-----\\nMIIaA0n\\n-----END PRIVATE KEY-----\\n"
                     :client-email   "1234-compute@developer.gserviceaccount.com"
                     :client-id      "98765"
                     :services       [{:href "service/service-1"}
                                      {:href "service/service-2"}]
                     }]
              (is (s/valid? ::service-spec/schema root))
              (doseq [k (into #{} (keys root))]
                     (is (not (s/valid? ::service-spec/schema (dissoc root k)))))))
