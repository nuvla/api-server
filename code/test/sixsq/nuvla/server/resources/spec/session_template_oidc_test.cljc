(ns sixsq.nuvla.server.resources.spec.session-template-oidc-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.spec.session-template-oidc :as st-oidc]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners ["group/nuvla-admin"]})


(deftest check-session-template-oidc-schema
  (let [timestamp "1964-08-25T10:00:00Z"
        cfg       {:id            (str st/resource-type "/oidc")
                   :resource-type st/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :method        "oidc"
                   :instance      "oidc"
                   :group         "OIDC Authentication"
                   :redirect-url  "https://nuv.la/webui/profile"}]

    (stu/is-valid ::st-oidc/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :instance}]
      (stu/is-invalid ::st-oidc/schema (dissoc cfg attr)))

    (doseq [attr #{:group :redirect-url}]
      (stu/is-valid ::st-oidc/schema (dissoc cfg attr)))))
