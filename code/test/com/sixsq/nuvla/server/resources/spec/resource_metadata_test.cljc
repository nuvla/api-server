(ns com.sixsq.nuvla.server.resources.spec.resource-metadata-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.resources.resource-metadata :as t]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata :as spec]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-action-test :as action]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-attribute-test :as attribute]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-capability-test :as capability]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def timestamp "1964-08-25T10:00:00.00Z")


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(def common {:id            (str t/resource-type "/abcdef")
             :resource-type t/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl})


(def valid-contents {:type-uri     "https://sixsq.com/slipstream/SomeResource"
                     :actions      [action/valid]
                     :attributes   attribute/valid-attributes
                     :capabilities [capability/valid]})


(def valid (merge common valid-contents))


(deftest check-resource-metadata

  (stu/is-valid ::spec/resource-metadata valid)

  (doseq [attr #{:id :resource-type :created :updated :acl :type-uri}]
    (stu/is-invalid ::spec/resource-metadata (dissoc valid attr)))

  (doseq [attr #{:actions :attributes :capabilities :vscope}]
    (stu/is-valid ::spec/resource-metadata (dissoc valid attr)))

  (stu/is-invalid ::spec/resource-metadata (assoc valid :badAttribute 1)))
