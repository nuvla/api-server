(ns com.sixsq.slipstream.ssclj.resources.spec.user-params-template-exec-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]

    [com.sixsq.slipstream.ssclj.resources.spec.user-params-template-exec]
    [com.sixsq.slipstream.ssclj.resources.user-params-template :as upt]
    [com.sixsq.slipstream.ssclj.resources.user-params-template-exec :as upte]))

(def valid-acl {:owner {:principal "USER"
                        :type      "ROLE"}
                :rules [{:principal "USER"
                         :type      "ROLE"
                         :right     "VIEW"}]})

(def common-req-attrs #{:id :resourceURI :updated :created :acl})

(defn only-not-common-req-attrs
  [m]
  (filter #(not (contains? common-req-attrs %)) (keys m)))

(deftest test-schema-check
  (let [timestamp "1972-10-08T10:00:00.0Z"
        root      {:id                  (str upt/resource-url "/" upte/params-type)
                   :resourceURI         upt/resource-uri
                   :created             timestamp
                   :updated             timestamp
                   :acl                 valid-acl

                   :paramsType          upte/params-type
                   :defaultCloudService "foo"
                   :keepRunning         "bar"
                   :mailUsage           "baz"
                   :verbosityLevel      0
                   :sshPublicKey        ""
                   :timeout             0}]
    (is (s/valid? :cimi/user-params-template.exec root))
    (doseq [k (only-not-common-req-attrs root)]
      (is (not (s/valid? :cimi/user-params-template.exec (dissoc root k)))))))
