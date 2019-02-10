(ns sixsq.nuvla.server.resources.spec.configuration-template-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.spec.configuration-template :as cts]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::configuration-template (su/only-keys-maps cts/resource-keys-spec))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:principal "ANON"
                         :type      "ROLE"
                         :right     "VIEW"}]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        root {:id            (str ct/resource-type "/test")
              :resource-type p/service-context
              :created       timestamp
              :updated       timestamp
              :acl           valid-acl
              :service       "cloud-software-solution"}]

    (stu/is-valid ::configuration-template root)

    (doseq [k (into #{} (keys (dissoc root :id :resource-type)))]
      (stu/is-invalid ::configuration-template (dissoc root k)))))
