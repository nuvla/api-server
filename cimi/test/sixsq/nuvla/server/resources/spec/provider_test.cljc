(ns sixsq.nuvla.server.resources.spec.provider-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.service :as provider-resource]
    [sixsq.nuvla.server.resources.spec.provider :as provider]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "jane"
                         :type      "USER"
                         :right     "VIEW"}]})


(deftest check-provider
  (let [timestamp "1964-08-25T10:00:00.0Z"
        provider {:id            (str provider-resource/resource-type "/uuid")
                  :resource-type provider-resource/resource-type
                  :created       timestamp
                  :updated       timestamp
                  :acl           valid-acl

                  :documentation "http://example.com/documentation"
                  :services      [{:href "service/service-1"}
                                  {:href "service/service-2"}]}]

    (stu/is-valid ::provider/schema provider)

    (stu/is-valid ::provider/schema (assoc provider :services []))

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl}]
      (stu/is-invalid ::provider/schema (dissoc provider k)))

    ;; optional keywords
    (doseq [k #{:documentation :services}]
      (stu/is-valid ::provider/schema (dissoc provider k)))))
