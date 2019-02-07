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
        root {:id                     (str t/resource-url "/connector-uuid")
              :resource-type            t/resource-uri
              :created                timestamp
              :updated                timestamp
              :acl                    valid-acl
              :parentPath             "a/b"
              :path                   "a/b/c"
              :type                   "IMAGE"
              :versions               [{:href   "module-image/xyz"
                                        :author "someone"
                                        :commit "wip"}
                                       nil
                                       {:href "module-image/abc"}]
              :logoURL                "https://example.org/logo"

              :dataAcceptContentTypes ["application/json" "application/x-something"]
              :dataAccessProtocols    ["http+s3" "posix+nfs"]}]

    (stu/is-valid ::module/module root)
    (stu/is-invalid ::module/module (assoc root :badKey "badValue"))
    (stu/is-invalid ::module/module (assoc root :type "BAD_VALUE"))

    ;; required attributes
    (doseq [k #{:id :resource-type :created :updated :acl :path :type}]
      (stu/is-invalid ::module/module (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:logoURL :versions :dataAcceptContentTypes :dataAccessProtocols}]
      (stu/is-valid ::module/module (dissoc root k)))))
