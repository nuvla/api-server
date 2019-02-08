(ns sixsq.nuvla.server.resources.spec.service-attribute-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.service-attribute :as sa-resource]
    [sixsq.nuvla.server.resources.spec.service-attribute :as sa]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest check-attribute
  (let [timestamp "1964-08-25T10:00:00.0Z"
        attr {:id            (str sa-resource/resource-type "/test-attribute")
              :name          "Test Attribute"
              :description   "A attribute containing a test value."
              :resource-type sa-resource/resource-uri
              :created       timestamp
              :updated       timestamp
              :acl           valid-acl

              :prefix        "example-org"
              :attributeName "test-attribute"
              :type          "string"}]


    (stu/is-valid ::sa/service-attribute attr)

    (stu/is-invalid ::sa/service-attribute (assoc attr :prefix 0))
    (stu/is-invalid ::sa/service-attribute (assoc attr :prefix ""))

    (stu/is-invalid ::sa/service-attribute (assoc attr :attributeName 0))
    (stu/is-invalid ::sa/service-attribute (assoc attr :attributeName ""))

    (stu/is-valid ::sa/service-attribute (assoc attr :type "string"))

    ;; mandatory keywords
    (doseq [k #{:id :name :description :created :updated :acl :prefix :attributeName :type}]
      (stu/is-invalid ::sa/service-attribute (dissoc attr k)))))
