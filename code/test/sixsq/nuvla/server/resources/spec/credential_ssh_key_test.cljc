(ns sixsq.nuvla.server.resources.spec.credential-ssh-key-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-ssh-key :as ssh-key]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners   ["group/nuvla-admin"]
   :view-acl ["user/jane"]})


(deftest check-credential-ssh
  (let [timestamp "1964-08-25T10:00:00.00Z"
        tpl       {:id            (str cred/resource-type "/uuid")
                   :resource-type cred/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :subtype       "ssh-key"
                   :method        "ssh-key"

                   :public-key    "some-ssh-key"
                   :private-key   "****"}]

    (stu/is-valid ::ssh-key/schema tpl)

    ;; there are no mandatory keywords
    (doseq [k #{:public-key :private-key}]
      (stu/is-valid ::ssh-key/schema (dissoc tpl k)))))
