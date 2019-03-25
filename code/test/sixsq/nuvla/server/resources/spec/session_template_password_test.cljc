(ns sixsq.nuvla.server.resources.spec.session-template-password-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.spec.session-template-password :as session-tpl]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(deftest check-session-template-password-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id            (str st/resource-type "/password")
             :resource-type st/resource-type
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :method        "password"
             :instance      "password"
             :group         "Federated Identity"
             :redirectURI   "https://nuv.la/webui/profile"

             :username      "user"
             :password      "pass"}]

    (stu/is-valid ::session-tpl/schema cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :instance :username :password}]
      (stu/is-invalid ::session-tpl/schema (dissoc cfg attr)))

    (doseq [attr #{:group :redirectURI}]
      (stu/is-valid ::session-tpl/schema (dissoc cfg attr)))))
