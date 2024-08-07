(ns com.sixsq.nuvla.server.resources.spec.data-record-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.data-record :as data-record-resource]
    [com.sixsq.nuvla.server.resources.spec.data-record :as data-record]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest check-data-record
  (let [timestamp   "1964-08-25T10:00:00.00Z"

        location    [6.143158 46.204391 373.0]

        data-record {:id                     (str data-record-resource/resource-type "/uuid")
                     :resource-type          data-record-resource/resource-type
                     :created                timestamp
                     :updated                timestamp
                     :acl                    valid-acl
                     :infrastructure-service "infrastructure-service/my-service-uuid"
                     :other                  "value"

                     :content-type           "text/html; charset=utf-8"
                     :bytes                  10234
                     :md5sum                 "abcde"
                     :timestamp              timestamp
                     :location               location

                     :mount                  {:mount-type     "volume"
                                              :target         "/mnt/bucket"
                                              :volume-options {:o      "addr=127.0.0.1"
                                                               :type   "nfs"
                                                               :device ":/data/bucket"}}}]

    (stu/is-valid ::data-record/schema data-record)

    ;; mandatory keywords
    (doseq [k #{:created :updated :acl :infrastructure-service}]
      (stu/is-invalid ::data-record/schema (dissoc data-record k)))

    ;; optional keywords
    (doseq [k #{:other :content-type :bytes :md5sum :timestamp :location :mount}]
      (stu/is-valid ::data-record/schema (dissoc data-record k)))))
