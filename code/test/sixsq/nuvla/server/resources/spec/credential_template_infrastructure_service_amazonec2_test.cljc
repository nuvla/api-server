(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-amazonec2-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-amazonec2 :as service]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-amazonec2 :as service-spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl service/resource-acl)


(deftest test-credential-template-service-schema-check
  (let [timestamp "1972-10-08T10:00:00.0Z"
        tpl {:id                      (str ct/resource-type "/uuid")
             :resource-type           p/resource-type
             :created                 timestamp
             :updated                 timestamp
             :acl                     valid-acl
             :type                    service/credential-type
             :method                  service/method
             :infrastructure-services ["infrastructure-service/service-1"
                                       "infrastructure-service/service-2"]
             :amazonec2-access-key    "foo"
             :amazonec2-secret-key    "bar"}]

    (stu/is-valid ::service-spec/schema tpl)

    ;; mandatory keys
    (doseq [k (-> tpl (dissoc :infrastructure-services) keys set)]
      (stu/is-invalid ::service-spec/schema (dissoc tpl k)))

    ;; optional keys
    (doseq [k #{:infrastructure-services}]
      (stu/is-valid ::service-spec/schema (dissoc tpl k)))))
