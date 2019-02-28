(ns sixsq.nuvla.server.resources.spec.module-component-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.module-component :as t]
    [sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id            (str t/resource-type "/module-component-uuid")
              :resource-type t/resource-type
              :created       timestamp
              :updated       timestamp
              :acl           valid-acl

              :author        "someone"
              :commit        "wip"

              :architecture  "x86"
              :image         "ubuntu:16.04"
              :ports         ["8022:22"]
              :urls          [["primary" "https://${host}:${port-443}/my/path"]
                              ["other" "http://${host}:${port-80}/path"]]}]

    (stu/is-valid ::module-component/module-component root)
    (stu/is-invalid ::module-component/module-component (assoc root :badKey "badValue"))
    (stu/is-invalid ::module-component/module-component (assoc root :os "BAD_OS"))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :author :architecture :image}]
      (stu/is-invalid ::module-component/module-component (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:commit :ports :urls}]
      (stu/is-valid ::module-component/module-component (dissoc root k)))))
