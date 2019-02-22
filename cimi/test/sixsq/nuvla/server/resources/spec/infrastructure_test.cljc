(ns sixsq.nuvla.server.resources.spec.infrastructure-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.service :as infra-resource]
    [sixsq.nuvla.server.resources.spec.infrastructure :as infra]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "jane"
                         :type      "USER"
                         :right     "VIEW"}]})


(deftest check-infrastructure
  (let [timestamp "1964-08-25T10:00:00.0Z"
        infra {:id            (str infra-resource/resource-type "/uuid")
               :resource-type infra-resource/resource-type
               :created       timestamp
               :updated       timestamp
               :acl           valid-acl

               :services      [{:href "service/service-1"}
                               {:href "service/service-2"}]}]

    (stu/is-valid ::infra/schema infra)

    (stu/is-invalid ::infra/schema (assoc infra :services []))

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::infra/schema (dissoc infra k)))

    ;; optional keywords
    (doseq [k #{:services}]
      (stu/is-valid ::infra/schema (dissoc infra k)))))
