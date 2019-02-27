(ns sixsq.nuvla.server.resources.spec.module-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.module :as t]
    [sixsq.nuvla.server.resources.spec.module :as module]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id                        (str t/resource-type "/connector-uuid")
              :resource-type             t/resource-type
              :created                   timestamp
              :updated                   timestamp
              :acl                       valid-acl
              :parent-path               "a/b"
              :path                      "a/b/c"
              :type                      "IMAGE"
              :versions                  [{:href   "module-image/xyz"
                                           :author "someone"
                                           :commit "wip"}
                                          nil
                                          {:href "module-image/abc"}]
              :logo-url                  "https://example.org/logo"

              :data-accept-content-types ["application/json" "application/x-something"]
              :data-access-protocols     ["http+s3" "posix+nfs"]}]

    (stu/is-valid ::module/module root)
    (stu/is-invalid ::module/module (assoc root :bad-key "badValue"))
    (stu/is-invalid ::module/module (assoc root :type "BAD_VALUE"))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :path :type}]
      (stu/is-invalid ::module/module (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:logo-url :versions :data-accept-content-types :data-access-protocols}]
      (stu/is-valid ::module/module (dissoc root k)))))
