(ns sixsq.nuvla.server.middleware.authn-info-test
  (:require
    [clojure.test :refer [are deftest]]
    [ring.util.codec :as codec]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.middleware.authn-info :as t]))


(defn serialize-cookie-value
  "replaces the map cookie value with a serialized string"
  [{:keys [value] :as cookie}]
  (assoc cookie :value (codec/form-encode value)))


(def session "session/2ba95fe4-7bf0-495d-9954-251d7417b3ce")


(def session-a "session/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")


(def cookie-id (serialize-cookie-value (cookies/create-cookie {:user-id "user/uname2"
                                                               :claims  "group/nuvla-user user/uname2"})))


(def cookie-id-roles (serialize-cookie-value
                       (cookies/create-cookie {:user-id "user/uname2"
                                               :claims  (str "group/nuvla-user group/alpha-role user/uname2 " session-a)
                                               :session session-a})))


(deftest check-is-session?
  (are [expected s] (= expected (t/is-session? s))
                    nil nil
                    nil ""
                    nil "group/nuvla-user"
                    session session
                    session-a session-a))


(deftest check-extract-authn-info
  (are [expected header]
    (= expected (t/extract-header-authn-info {:headers {t/authn-info-header header}}))
    nil nil
    nil ""
    {:user-id "user/uname"
     :claims  #{"group/nuvla-anon" "user/uname"}} "user/uname"
    {:user-id "user/uname"
     :claims  #{"group/nuvla-anon"
                "user/uname"}} "  user/uname"
    {:claims #{"group/nuvla-anon" "user/uname" "group/r1"} :user-id "user/uname"} "user/uname group/r1"
    {:claims #{"group/nuvla-anon" "user/uname" "group/r1"} :user-id "user/uname"} "  user/uname group/r1"
    {:claims #{"group/nuvla-anon" "user/uname" "group/r1"} :user-id "user/uname"} "user/uname group/r1  "
    {:claims #{"group/nuvla-anon" "user/uname" "group/r1" "group/r2"} :user-id "user/uname"} "user/uname group/r1 group/r2"))


(deftest check-cookie-info->authn-info
  (are [expected cookie-info]
    (= expected (t/cookie-info->authn-info cookie-info))
    nil nil
    nil {}
    {:claims #{} :user-id "user"} {:user-id "user"}
    {:claims #{}, :session "session", :user-id "user"} {:user-id "user", :session "session"}
    {:claims #{"role1"}, :user-id "user"} {:user-id "user", :claims "role1"}
    {:claims #{"role1", "role2"}, :user-id "user"} {:user-id "user", :claims "role1 role2"}
    {:claims #{"role1"}, :session "session", :user-id "user"} {:user-id "user", :claims "role1",
                                                                          :session "session"}
    {:claims #{"role1", "role2"}, :session "session", :user-id "user"} {:user-id "user",
                                                                                   :claims  "role1 role2",
                                                                                   :session "session"}
    {:claims #{"role1", "role2"}, :session "session", :user-id "role2"} {:user-id      "user",
                                                                                    :claims       "role1 role2",
                                                                                    :session      "session"
                                                                                    :active-claim "role2"}))



(deftest check-handler
  (let [handler  (t/wrap-authn-info identity)
        anon-map {:claims #{"group/nuvla-anon"}}]
    (are [expected request] (= expected (auth/current-authentication (handler request)))
                            anon-map {}
                            anon-map {:headers {"header-1" "value"}}
                            anon-map {:headers {t/authn-info-header nil}}
                            anon-map {:headers {t/authn-info-header ""}}

                            {:claims  #{"group/nuvla-anon", "user/uname"}
                             :user-id "user/uname"} {:headers {t/authn-info-header "user/uname"}}

                            {:claims  #{"group/r1", "group/nuvla-anon", "user/uname"}
                             :user-id "user/uname"} {:headers {t/authn-info-header "user/uname group/r1"}}

                            {:claims  #{"group/r1", "group/r2", "group/nuvla-anon", "user/uname"}
                             :user-id "user/uname"} {:headers {t/authn-info-header "user/uname group/r1 group/r2"}}

                            {:claims  #{"group/nuvla-anon", "user/uname2", "group/nuvla-user"}
                             :user-id "user/uname2"} {:cookies {t/authn-cookie cookie-id}}

                            {:claims  #{"group/nuvla-user", "group/alpha-role",
                                        session-a, "group/nuvla-anon", "user/uname2"}
                             :user-id "user/uname2"} {:cookies {t/authn-cookie cookie-id-roles}}

                            {:claims  #{"group/r1", "group/r2", "group/nuvla-anon", "user/uname"}
                             :user-id "user/uname"} {:headers {t/authn-info-header "user/uname group/r1 group/r2"}
                                                     :cookies {t/authn-cookie cookie-id-roles}})))

