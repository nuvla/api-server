(ns sixsq.nuvla.server.resources.spec.acl-resource-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.acl-resource :as acl-resource]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(deftest check-collection-acl

  (let [acl {:owners    ["user/id1" "user/id2"]
             :view-meta ["user/id1" "user/id2" "group/nuvla-anon"]
             :view-data ["user/id1" "user/id2"]
             :view-acl  ["user/id1"]
             :edit-meta ["user/id1" "user/id2"]
             :edit-data ["user/id1" "user/id2"]
             :edit-acl  ["user/id1"]
             :delete    ["user/id1"]
             :manage    ["user/id1"]}]

    ;; empty map is invalid
    (stu/is-invalid ::acl-resource/acl {})

    ;; owners is required
    (doseq [k #{:owners}]
      (stu/is-invalid ::acl-resource/acl (dissoc acl k)))

    ;; owners cannot be empty
    (doseq [k #{:owners}]
      (stu/is-invalid ::acl-resource/acl (assoc acl k [])))

    ;; no duplicate owners
    (doseq [k #{:owners}]
      (stu/is-invalid ::acl-resource/acl (assoc acl k ["user/id1" "user/id1"])))

    ;; everything but owners is optional
    (doseq [k (-> acl (dissoc :owners) keys set)]
      (stu/is-valid ::acl-resource/acl (dissoc acl k)))

    ;; everything but owners can be empty
    (doseq [k (-> acl (dissoc :owners) keys set)]
      (stu/is-valid ::acl-resource/acl (assoc acl k [])))))
