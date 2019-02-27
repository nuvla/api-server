(ns sixsq.nuvla.server.resources.spec.credential-template-service-exoscale-test
    (:require
      [clojure.spec.alpha :as s]
      [clojure.test :refer :all]
      [sixsq.nuvla.server.resources.credential :as p]
      [sixsq.nuvla.server.resources.credential-template :as ct]
      [sixsq.nuvla.server.resources.credential-template-service-exoscale :as service]
      [sixsq.nuvla.server.resources.spec.credential-template-service-exoscale :as service-spec]))


(def valid-acl service/resource-acl)


(deftest test-credential-template-driver-exoscale-schema-check
         (let [timestamp "1972-10-08T10:00:00.0Z"
               root {:id                      (str ct/resource-type "/uuid")
                     :resource-type           p/resource-type
                     :created                 timestamp
                     :updated                 timestamp
                     :acl                     valid-acl
                     :type                    service/credential-type
                     :method                  service/method
                     :services      [{:href "service/service-1"}
                                     {:href "service/service-2"}]
                     :exoscale-api-key        "foo"
                     :exoscale-api-secret-key "bar"
                     }]
              (is (s/valid? ::service-spec/schema root))
              (doseq [k (into #{} (keys root))]
                     (is (not (s/valid? ::service-spec/schema (dissoc root k)))))))
