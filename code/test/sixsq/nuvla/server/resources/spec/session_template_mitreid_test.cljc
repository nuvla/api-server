(ns sixsq.nuvla.server.resources.spec.session-template-mitreid-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.spec.session-template-mitreid :as st-mitreid]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(deftest check-session-template-mitreid-schema
  (let [timestamp "1964-08-25T10:00:00Z"
        cfg       {:id            (str st/resource-type "/mitreid")
                   :resource-type st/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :method        "mitreid"
                   :instance      "mitreid"
                   :group         "MITREid Authentication"
                   :redirect-url  "https://nuv.la/webui/profile"}]

    (stu/is-valid ::st-mitreid/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :instance}]
      (stu/is-invalid ::st-mitreid/schema (dissoc cfg attr)))

    (doseq [attr #{:group :redirect-url}]
      (stu/is-valid ::st-mitreid/schema (dissoc cfg attr)))))
