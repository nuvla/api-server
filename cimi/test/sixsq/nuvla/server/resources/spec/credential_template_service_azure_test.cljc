(ns sixsq.nuvla.server.resources.spec.credential-template-service-azure-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-service-azure :as service]
    [sixsq.nuvla.server.resources.spec.credential-template-service-azure :as service-spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl service/resource-acl)


(deftest test-credential-template-service-schema-check
  (let [timestamp "1972-10-08T10:00:00.0Z"
        tpl {:id                    (str ct/resource-type "/uuid")
             :resource-type         p/resource-type
             :created               timestamp
             :updated               timestamp
             :acl                   valid-acl
             :type                  service/credential-type
             :method                service/method
             :services              ["infrastructure-service/service-1"
                                     "infrastructure-service/service-2"]
             :azure-client-id       "foo"
             :azure-client-secret   "barsecret"
             :azure-subscription-id "bar"}]

    (is (s/valid? ::service-spec/schema tpl))

    ;; mandatory keys
    (doseq [k (-> tpl (dissoc :services) keys set)]
      (stu/is-invalid ::service-spec/schema (dissoc tpl k)))

    ;; optional keys
    (doseq [k #{:services}]
      (stu/is-valid ::service-spec/schema (dissoc tpl k)))))
