(ns sixsq.nuvla.server.resources.spec.infrastructure-service-group-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.infrastructure-service :as service]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-group :as infrastructure-service-group]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(deftest check-service-group
  (let [timestamp     "1964-08-25T10:00:00.00Z"
        service-group {:id                      (str service/resource-type "/uuid")
                       :resource-type           service/resource-type
                       :created                 timestamp
                       :updated                 timestamp
                       :acl                     valid-acl

                       :documentation           "http://example.com/documentation"
                       :infrastructure-services [{:href "infrastructure-service/service-1"}
                                                 {:href "infrastructure-service/service-2"}]}]

    (stu/is-valid ::infrastructure-service-group/schema service-group)

    (stu/is-valid ::infrastructure-service-group/schema (assoc service-group :infrastructure-services []))

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::infrastructure-service-group/schema (dissoc service-group k)))

    ;; optional keywords
    (doseq [k #{:documentation :infrastructure-services}]
      (stu/is-valid ::infrastructure-service-group/schema (dissoc service-group k)))))
