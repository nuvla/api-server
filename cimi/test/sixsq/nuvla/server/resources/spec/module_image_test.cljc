(ns sixsq.nuvla.server.resources.spec.module-image-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.connector :as t]  ;; FIXME: Change to module-version when available.
    [sixsq.nuvla.server.resources.spec.module-image :as module-image]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id           (str t/resource-url "/connector-uuid")
              :resourceURI  t/resource-uri
              :created      timestamp
              :updated      timestamp
              :acl          valid-acl

              :os           "Ubuntu"
              :loginUser    "ubuntu"
              :sudo         true

              :cpu          2
              :ram          2048
              :disk         100
              :volatileDisk 500
              :networkType  "public"

              :imageIDs     {:some-cloud       "my-great-image-1"
                             :some-other-cloud "great-stuff"}

              :relatedImage {:href "module/other"}
              :author "someone"
              :commit "wip"}]

    (stu/is-valid ::module-image/module-image root)
    (stu/is-invalid ::module-image/module-image (assoc root :badKey "badValue"))
    (stu/is-invalid ::module-image/module-image (assoc root :os "BAD_OS"))

    ;; required attributes
    (doseq [k #{:id :resourceURI :created :updated :acl :os :loginUser :networkType :author}]
      (stu/is-invalid ::module-image/module-image (dissoc root k)))

    ;; optional attributes
    (doseq [k #{:connectors :connectorClasses :sudo :relatedImage
                :cpu :ram :disk :volatileDisk :commit}]
      (stu/is-valid ::module-image/module-image (dissoc root k)))))
