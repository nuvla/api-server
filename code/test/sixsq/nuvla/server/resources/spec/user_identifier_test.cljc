(ns sixsq.nuvla.server.resources.spec.user-identifier-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.user-identifier :as user-identifier]
    [sixsq.nuvla.server.resources.user-identifier :as ui]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest check-session-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id            (str ui/resource-type "/hash-of-identifier")
             :resource-type ui/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :parent        "user/abc"
             :identifier    "some-long-identifier"}]

    (stu/is-valid ::user-identifier/schema cfg)
    (stu/is-invalid ::user-identifier/schema (assoc cfg :bad-attr "BAD_ATTR"))

    (doseq [attr #{:id :resource-type :created :updated :acl :identifier}]
      (stu/is-invalid ::user-identifier/schema (dissoc cfg attr)))

    (doseq [attr #{:username :server :clientIP}]
      (stu/is-valid ::user-identifier/schema (dissoc cfg attr)))))
