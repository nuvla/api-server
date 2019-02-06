(ns sixsq.nuvla.server.resources.spec.session-template-github-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.spec.session-template-github :as st-github]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest check-session-template-github-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id          (str st/resource-url "/github")
             :resourceURI st/resource-uri
             :created     timestamp
             :updated     timestamp
             :acl         valid-acl

             :method      "github"
             :instance    "github"
             :group       "GitHub Authentication"
             :redirectURI "https://nuv.la/webui/profile"}]

    (stu/is-valid ::st-github/schema cfg)

    (doseq [attr #{:id :resourceURI :created :updated :acl :method :instance}]
      (stu/is-invalid ::st-github/schema (dissoc cfg attr)))

    (doseq [attr #{:group :redirectURI}]
      (stu/is-valid ::st-github/schema (dissoc cfg attr)))))
