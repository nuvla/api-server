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
                     :state         do/state-new

                     :credential    "credential/cloud-cred"
                     :bucket        "bucket"
                     :object        "object/name"

                     :template      "data-object-template/generic"

                     :content-type  "text/plain"
                     :bytes         42
                     :md5sum        "3deb5ba5d971c85dd979b7466debfdee"
                     :timestamp     timestamp
                     :location      {:lon 0.0
                                     :lat 0.0
                                     :alt 0.0}})]

    (stu/is-valid ::do-generic/schema root)

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl
                :type :state :credential :bucket :object}]
      (stu/is-invalid ::do-generic/schema (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:href :content-type :bytes :md5sum :timestamp :location}]
      (stu/is-valid ::do-generic/schema (dissoc root k)))))
