(ns com.sixsq.nuvla.server.resources.spec.configuration-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.server.resources.configuration :as t]
    [com.sixsq.nuvla.server.resources.spec.configuration-template :as cts]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [com.sixsq.nuvla.server.util.spec :as su]))


(s/def ::configuration (su/only-keys-maps cts/resource-keys-spec))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest test-configuration-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        cfg       {:id            (str t/resource-type "/test")
                   :resource-type t/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl
                   :service       "foo"}]

    (is (stu/is-valid ::configuration cfg))

    (doseq [k (into #{} (keys (dissoc cfg :id :resource-type)))]
      (stu/is-invalid ::configuration (dissoc cfg k)))))
