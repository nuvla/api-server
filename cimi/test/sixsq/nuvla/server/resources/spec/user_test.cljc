(ns sixsq.nuvla.server.resources.spec.user-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.user :as user]
    [sixsq.nuvla.server.resources.user :refer :all]))


(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})


(deftest check-user-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        uname "120720737412@eduid.chhttps://eduid.ch/idp/shibboleth!https://fed-id.nuv.la/samlbridge/module.php/saml/sp/metadata.php/sixsq-saml-bridge!iqqrh4oiyshzcw9o40cvo0+pgka="
        cfg {:id            (str resource-type "/" uname)
             :resource-type resource-uri
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl

             :username      uname
             :emailAddress  "me@example.com"

             :full-name     "John"
             :method        "direct"
             :href          "user-template/direct"
             :password      "hashed-password"
             :isSuperUser   false
             :state         "ACTIVE"
             :deleted       false
             :name          "me@example.com"}]

    (stu/is-valid ::user/schema cfg)
    (stu/is-invalid ::user/schema (assoc cfg :unknown "value"))

    (doseq [attr #{:id :resource-type :created :updated :acl :username :emailAddress}]
      (stu/is-invalid ::user/schema (dissoc cfg attr)))

    (doseq [attr #{:full-name :method :href :password
                   :roles :isSuperUser :state :deleted :name}]
      (stu/is-valid ::user/schema (dissoc cfg attr)))))
