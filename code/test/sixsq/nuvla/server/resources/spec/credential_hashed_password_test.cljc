(ns sixsq.nuvla.server.resources.spec.credential-hashed-password-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-hashed-password :as hashed-pwd]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners   ["group/nuvla-admin"]
   :view-acl ["user/jane"]})


(deftest check-credential-service-docker
  (let [timestamp "1964-08-25T10:00:00.00Z"
        tpl {:id            (str cred/resource-type "/uuid")
             :resource-type cred/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :type          "swarm"
             :method        "swarm"

             :hash          "some-hash-of-a-password"}]

    (stu/is-valid ::hashed-pwd/schema tpl)

    ;; mandatory keywords
    (doseq [k (-> tpl keys set)]
      (stu/is-invalid ::hashed-pwd/schema (dissoc tpl k)))))
