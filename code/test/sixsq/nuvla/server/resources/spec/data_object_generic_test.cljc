(ns sixsq.nuvla.server.resources.spec.data-object-generic-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.data-object :as do]
    [sixsq.nuvla.server.resources.data-object-template :as dot]
    [sixsq.nuvla.server.resources.data-object-template-generic :as tpl]
    [sixsq.nuvla.server.resources.spec.data-object-generic :as do-generic]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-user"]})

(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root (merge tpl/resource
                    {:id            "data-object/my-report"
                     :resource-type dot/resource-type
                     :created       timestamp
                     :updated       timestamp
                     :acl           valid-acl
                     :state         do/state-new})]

    (stu/is-valid ::do-generic/data-object root)

    (stu/is-valid ::do-generic/data-object
                  (merge root {:content-type "content-type"
                               :bytes        42
                               :md5sum       "3deb5ba5d971c85dd979b7466debfdee"}))

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl
                :type :state :object :bucket :credential}]
      (stu/is-invalid ::do-generic/data-object (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:content-type :bytes :md5sum}]
      (stu/is-valid ::do-generic/data-object (dissoc root k)))))
