(ns sixsq.nuvla.server.resources.spec.deployment-template-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.module :as t]
    [sixsq.nuvla.server.resources.spec.deployment-template :as dmt]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id               (str t/resource-url "/dmt-uuid")
              :resourceURI      t/resource-uri
              :created          timestamp
              :updated          timestamp
              :acl              valid-acl

              :module           {:href "module/my-image"}
              :outputParameters [{:parameter "param-1"}]}]

    (stu/is-valid ::dmt/deployment-template root)
    (stu/is-invalid ::dmt/deployment-template (assoc root :badKey "badValue"))

    ;; required attributes
    (doseq [k #{:id :resourceURI :created :updated :acl :module}]
      (stu/is-invalid ::dmt/deployment-template (dissoc root k)))))
