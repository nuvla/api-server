(ns sixsq.nuvla.server.resources.spec.data-object-public-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.data-object :as do]
    [sixsq.nuvla.server.resources.data-object-template :as dot]
    [sixsq.nuvla.server.resources.data-object-template-public :as tpl]
    [sixsq.nuvla.server.resources.spec.data-object-public :as do-public]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "USER"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root (merge tpl/resource
                    {:id            "data-object/my-public-object"
                     :resource-type dot/resource-type
                     :created       timestamp
                     :updated       timestamp
                     :acl           valid-acl
                     :state         do/state-new
                     :url           "http://bucket.s3.com"})]

    (stu/is-valid ::do-public/data-object root)

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl
                :type :state :object :bucket :credential}]
      (stu/is-invalid ::do-public/data-object (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:url}]
      (stu/is-valid ::do-public/data-object (dissoc root k)))))
