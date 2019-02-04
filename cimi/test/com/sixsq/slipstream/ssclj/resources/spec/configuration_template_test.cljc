(ns com.sixsq.slipstream.ssclj.resources.spec.configuration-template-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.resources.configuration-template :as ct]
    [com.sixsq.slipstream.ssclj.resources.spec.configuration-template :as cts]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::configuration-template (su/only-keys-maps cts/resource-keys-spec))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id          (str ct/resource-url "/test")
              :resourceURI p/service-context
              :created     timestamp
              :updated     timestamp
              :acl         valid-acl
              :service     "cloud-software-solution"}]

    (stu/is-valid ::configuration-template root)

    (doseq [k (into #{} (keys (dissoc root :id :resourceURI)))]
      (stu/is-invalid ::configuration-template (dissoc root k)))))
