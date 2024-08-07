(ns com.sixsq.nuvla.server.resources.spec.data-object-generic-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.data-object :as do]
    [com.sixsq.nuvla.server.resources.data-object-template :as dot]
    [com.sixsq.nuvla.server.resources.data-object-template-generic :as tpl]
    [com.sixsq.nuvla.server.resources.spec.data-object-generic :as do-generic]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-user"]})

(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root      (merge tpl/resource
                         {:id            "data-object/my-report"
                          :resource-type dot/resource-type
                          :created       timestamp
                          :updated       timestamp
                          :acl           valid-acl
                          :state         do/state-new

                          :credential    "credential/d3167d53-0138-4754-b8fd-df8119474e7f"
                          :bucket        "bucket"
                          :object        "object/name"

                          :template      "data-object-template/generic"

                          :content-type  "text/plain"
                          :bytes         42
                          :md5sum        "3deb5ba5d971c85dd979b7466debfdee"
                          :timestamp     timestamp
                          :location      [0.0 0.0 0.0]})]

    (stu/is-valid ::do-generic/schema root)

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl
                :subtype :state :credential :bucket :object}]
      (stu/is-invalid ::do-generic/schema (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:href :content-type :bytes :md5sum :timestamp :location}]
      (stu/is-valid ::do-generic/schema (dissoc root k)))))
