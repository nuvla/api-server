(ns sixsq.nuvla.server.resources.spec.acl-collection-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.acl-collection :as acl-collection]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(deftest check-collection-acl

  (let [acl {:query       ["user/id1" "group/nuvla-admin"]
             :add         ["group/nuvla-user"]
             :bulk-delete ["group/nuvla-user"]}]

    ;; empty map is valid
    (stu/is-valid ::acl-collection/acl {})

    ;; none of the fields are required
    (doseq [k (-> acl keys set)]
      (stu/is-valid ::acl-collection/acl (dissoc acl k)))

    ;; all fields can be empty
    (doseq [k (-> acl keys set)]
      (stu/is-valid ::acl-collection/acl (assoc acl k [])))))
