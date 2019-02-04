(ns com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.ssclj.resources.email :as t]      ;; FIXME: Should be resource-metadata!!!
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata :as spec]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-action-test :as action]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-attribute-test :as attribute]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-capability-test :as capability]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-value-scope-test :as vscope]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))


(def timestamp "1964-08-25T10:00:00.0Z")


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(def common {:id          (str t/resource-url "/abcdef")
             :resourceURI t/resource-uri
             :created     timestamp
             :updated     timestamp
             :acl         valid-acl})


(def valid-contents {:typeURI      "https://sixsq.com/slipstream/SomeResource"
                     :actions      [action/valid]
                     :attributes   [attribute/valid]
                     :capabilities [capability/valid]
                     :vscope       vscope/valid})


(def valid (merge common valid-contents))


(deftest check-resource-metadata

  (stu/is-valid ::spec/resource-metadata valid)

  (doseq [attr #{:id :resourceURI :created :updated :acl :typeURI}]
    (stu/is-invalid ::spec/resource-metadata (dissoc valid attr)))

  (doseq [attr #{:actions :attributes :capabilities :vscope}]
    (stu/is-valid ::spec/resource-metadata (dissoc valid attr)))

  (stu/is-invalid ::spec/resource-metadata (assoc valid :badAttribute 1)))
