(ns sixsq.nuvla.server.resources.spec.session-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.session :refer :all]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))

(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})

(deftest check-session-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id            (str resource-type "/internal")
             :resource-type resource-uri
             :created       timestamp
             :updated       timestamp
             :acl           valid-acl
             :username      "ssuser"
             :method        "internal"
             :expiry        timestamp
             :server        "nuv.la"
             :clientIP      "127.0.0.1"
             :redirectURI   "https://nuv.la/webui/profile"
             :template      {:href "session-template/internal"}}]

    (stu/is-valid ::session/session cfg)

    (doseq [attr #{:id :resource-type :created :updated :acl :method :expiry :template}]
      (stu/is-invalid ::session/session (dissoc cfg attr)))

    (doseq [attr #{:username :server :clientIP}]
      (stu/is-valid ::session/session (dissoc cfg attr)))))
