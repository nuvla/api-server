(ns sixsq.nuvla.server.resources.spec.session-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.session :as t]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))

(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})

(deftest check-session-schema
  (let [timestamp "1964-08-25T10:00:00Z"
        cfg       {:id            (str t/resource-type "/internal")
                   :resource-type t/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl
                   :identifier    "ssuser"
                   :user          "user/abcdef01-abcd-abcd-abcd-abcdef012345"
                   :method        "internal"
                   :expiry        timestamp
                   :server        "nuv.la"
                   :client-ip     "127.0.0.1"
                   :redirect-url  "https://nuv.la/webui/profile"
                   :template      {:href "session-template/internal"}}]

    (stu/is-valid ::session/session cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :expiry :template}]
      (stu/is-invalid ::session/session (dissoc cfg attr)))

    (doseq [attr #{:identifier :user :server :client-ip}]
      (stu/is-valid ::session/session (dissoc cfg attr)))))
