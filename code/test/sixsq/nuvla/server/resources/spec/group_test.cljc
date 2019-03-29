(ns sixsq.nuvla.server.resources.spec.group-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.group :as t]
    [sixsq.nuvla.server.resources.spec.group :as group]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-group-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        group {:id            (str t/resource-type "/abcdef")
               :resource-type t/resource-type
               :created       timestamp
               :updated       timestamp
               :acl           valid-acl

               :users         ["user/aa2f41a3-c54c-fce8-32d2-0324e1c32e22"
                               "user/bb2f41a3-c54c-fce8-32d2-0324e1c32e22"
                               "user/cc2f41a3-c54c-fce8-32d2-0324e1c32e22"]}]

    (stu/is-valid ::group/schema group)

    (stu/is-valid ::group/schema (assoc group :users []))

    (stu/is-invalid ::group/schema (assoc group :bad "value"))

    ;; not distinct values
    (stu/is-invalid ::group/schema (assoc group :users ["user/aa2f41a3-c54c-fce8-32d2-0324e1c32e22"
                                                        "user/aa2f41a3-c54c-fce8-32d2-0324e1c32e22"]))

    ;; mandatory parameters
    (doseq [attr #{:id :resource-type :created :updated :acl :users}]
      (stu/is-invalid ::group/schema (dissoc group attr)))))
