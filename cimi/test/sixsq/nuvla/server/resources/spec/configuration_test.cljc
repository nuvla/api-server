(ns sixsq.nuvla.server.resources.spec.configuration-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.configuration :refer :all]
    [sixsq.nuvla.server.resources.configuration-template-nuvla :as tpl]
    [sixsq.nuvla.server.resources.spec.configuration-template :as cts]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::configuration (su/only-keys-maps cts/resource-keys-spec))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest test-configuration-schema-check
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id            (str resource-type "/test")
             :resource-type resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl
             :service       "foo"}]

    (is (stu/is-valid ::configuration cfg))

    (doseq [k (into #{} (keys (dissoc cfg :id :resource-type)))]
      (stu/is-invalid ::configuration (dissoc cfg k)))))
