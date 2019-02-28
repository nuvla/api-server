(ns sixsq.nuvla.server.resources.spec.credential-template-service-azure-test
    (:require
      [clojure.spec.alpha :as s]
      [clojure.test :refer :all]
      [sixsq.nuvla.server.resources.credential :as p]
      [sixsq.nuvla.server.resources.credential-template :as ct]
      [sixsq.nuvla.server.resources.credential-template-service-azure :as service]
      [sixsq.nuvla.server.resources.spec.credential-template-service-azure :as service-spec]))


(def valid-acl service/resource-acl)


(deftest test-credential-template-service-schema-check
         (let [timestamp "1972-10-08T10:00:00.0Z"
               root {:id                    (str ct/resource-type "/uuid")
                     :resource-type         p/resource-type
                     :created               timestamp
                     :updated               timestamp
                     :acl                   valid-acl
                     :type                  service/credential-type
                     :method                service/method
                     :services              [{:href "service/service-1"}
                                             {:href "service/service-2"}]
                     :azure-client-id       "foo"
                     :azure-client-secret   "barsecret"
                     :azure-subscription-id "bar"
                     }]
              (is (s/valid? ::service-spec/schema root))
              (doseq [k (into #{} (keys root))]
                     (is (not (s/valid? ::service-spec/schema (dissoc root k)))))))
