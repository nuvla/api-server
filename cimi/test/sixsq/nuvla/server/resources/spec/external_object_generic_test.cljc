(ns sixsq.nuvla.server.resources.spec.external-object-generic-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.external-object :as eo]
    [sixsq.nuvla.server.resources.external-object-template :as eot]
    [sixsq.nuvla.server.resources.external-object-template-generic :as tpl]
    [sixsq.nuvla.server.resources.spec.external-object-generic :as eo-generic]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "USER"
                         :type      "ROLE"
                         :right     "VIEW"}]})

(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root (merge tpl/resource
                    {:id          "external-object/my-report"
                     :resource-type eot/resource-uri
                     :created     timestamp
                     :updated     timestamp
                     :acl         valid-acl
                     :state       eo/state-new})]

    (stu/is-valid ::eo-generic/external-object root)

    (stu/is-valid ::eo-generic/external-object
                  (merge root {:contentType "contentType"
                               :size        42
                               :md5sum      "3deb5ba5d971c85dd979b7466debfdee"}))

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl
                :objectType :state :objectName :bucketName :objectStoreCred}]
      (stu/is-invalid ::eo-generic/external-object (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:contentType :size :md5sum}]
      (stu/is-valid ::eo-generic/external-object (dissoc root k)))))
