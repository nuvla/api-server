(ns com.sixsq.slipstream.ssclj.resources.spec.configuration-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.resources.configuration :refer :all]
    [com.sixsq.slipstream.ssclj.resources.configuration-template-slipstream :as tpl]
    [com.sixsq.slipstream.ssclj.resources.spec.configuration-template :as cts]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::configuration (su/only-keys-maps cts/resource-keys-spec))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest test-configuration-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id          (str resource-url "/slipstream")
             :resourceURI resource-uri
             :created     timestamp
             :updated     timestamp
             :acl         valid-acl
             :service     "foo"}]

    (is (stu/is-valid ::configuration cfg))

    (doseq [k (into #{} (keys (dissoc cfg :id :resourceURI)))]
      (stu/is-invalid ::configuration (dissoc cfg k)))))
