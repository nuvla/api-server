(ns com.sixsq.slipstream.ssclj.resources.spec.session-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.sixsq.slipstream.ssclj.resources.session :refer :all]
    [com.sixsq.slipstream.ssclj.resources.spec.session :as session]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]))

(def valid-acl {:owner {:principal "ADMIN"
                        :type      "ROLE"}
                :rules [{:type      "ROLE",
                         :principal "ADMIN",
                         :right     "ALL"}]})

(deftest check-session-schema
  (let [timestamp "1964-08-25T10:00:00.0Z"
        cfg {:id              (str resource-url "/internal")
             :resourceURI     resource-uri
             :created         timestamp
             :updated         timestamp
             :acl             valid-acl
             :username        "ssuser"
             :method          "internal"
             :expiry          timestamp
             :server          "nuv.la"
             :clientIP        "127.0.0.1"
             :redirectURI     "https://nuv.la/webui/profile"
             :sessionTemplate {:href "session-template/internal"}}]

    (stu/is-valid ::session/session cfg)

    (doseq [attr #{:id :resourceURI :created :updated :acl :method :expiry :sessionTemplate}]
      (stu/is-invalid ::session/session (dissoc cfg attr)))

    (doseq [attr #{:username :server :clientIP}]
      (stu/is-valid ::session/session (dissoc cfg attr)))))
