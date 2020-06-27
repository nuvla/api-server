(ns sixsq.nuvla.server.resources.spec.credential-template-ssh-key-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-ssh-key :as ssh-key]
    [sixsq.nuvla.server.resources.spec.credential-template-ssh-key :as ssh-key-spec]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners   ["group/nuvla-admin"]
   :view-acl ["user/jane"]})


(deftest test-credential-template-ssh-key-check
  (let [timestamp "1972-10-08T10:00:00.00Z"
        tpl       {:id            (str ct/resource-type "/uuid")
                   :resource-type p/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :subtype       ssh-key/credential-subtype
                   :method        ssh-key/method

                   :public-key    "ssh-key"
                   :private-key   "*****"}]

    (stu/is-valid ::ssh-key-spec/schema tpl)

    ;; optional keys
    (doseq [k #{:public-key :private-key}]
      (stu/is-valid ::ssh-key-spec/schema (dissoc tpl k)))))
