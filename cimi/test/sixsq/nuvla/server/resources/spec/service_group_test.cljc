(ns sixsq.nuvla.server.resources.spec.service-group-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.service :as service]
    [sixsq.nuvla.server.resources.spec.service-group :as service-group]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "jane"
                         :type      "USER"
                         :right     "VIEW"}]})


(deftest check-service-group
  (let [timestamp "1964-08-25T10:00:00.0Z"
        service-group {:id            (str service/resource-type "/uuid")
                       :resource-type service/resource-type
                       :created       timestamp
                       :updated       timestamp
                       :acl           valid-acl

                       :documentation "http://example.com/documentation"
                       :services      [{:href "service/service-1"}
                                       {:href "service/service-2"}]}]

    (stu/is-valid ::service-group/schema service-group)

    (stu/is-valid ::service-group/schema (assoc service-group :services []))

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::service-group/schema (dissoc service-group k)))

    ;; optional keywords
    (doseq [k #{:documentation :services}]
      (stu/is-valid ::service-group/schema (dissoc service-group k)))))
