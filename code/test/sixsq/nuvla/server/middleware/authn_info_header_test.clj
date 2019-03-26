(ns sixsq.nuvla.server.middleware.authn-info-header-test
  (:require
    [clojure.test :refer :all]
    [ring.util.codec :as codec]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.server.middleware.authn-info-header :refer :all]))

(defn serialize-cookie-value
  "replaces the map cookie value with a serialized string"
  [{:keys [value] :as cookie}]
  (assoc cookie :value (codec/form-encode value)))

(def session "session/2ba95fe4-7bf0-495d-9954-251d7417b3ce")
(def session-a "session/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

(def cookie-id (serialize-cookie-value (cookies/claims-cookie {:username "user/uname2"})))
(def cookie-id-roles (serialize-cookie-value
                       (cookies/claims-cookie {:username "user/uname2"
                                               :roles    "group/nuvla-user group/alpha-role"
                                               :session  session-a})))

(deftest check-is-session?
  (are [expected s] (= expected (is-session? s))
                    nil nil
                    nil ""
                    nil "group/nuvla-user"
                    session session
                    session-a session-a))

(deftest check-extract-authn-info
  (are [expected header] (= expected (extract-authn-info {:headers {authn-info-header header}}))
                         nil nil
                         nil ""
                         ["user/uname" #{}] "user/uname"
                         ["user/uname" #{}] "  user/uname"
                         ["user/uname" #{"group/r1"}] "user/uname group/r1"
                         ["user/uname" #{"group/r1"}] "  user/uname group/r1"
                         ["user/uname" #{"group/r1"}] "user/uname group/r1  "
                         ["user/uname" #{"group/r1" "group/r2"}] "user/uname group/r1 group/r2"))

(deftest check-extract-info
  (are [expected request] (= expected (extract-info request))
                          nil {}
                          ["user/uname" #{"group/r1"}] {:headers {authn-info-header "user/uname group/r1"}}
                          ["user/uname2" #{"group/nuvla-user" "group/alpha-role" session-a}]
                          {:cookies {authn-cookie cookie-id-roles}}
                          ["user/uname" #{"group/r1"}] {:headers {authn-info-header "user/uname group/r1"}
                                                        :cookies {authn-cookie cookie-id-roles}}))

(deftest check-extract-header-claims
  (are [expected header] (= expected (extract-header-claims {:headers {authn-info-header header}}))
                         nil nil
                         nil ""
                         {:username "user/uname"} "user/uname"
                         {:username "user/uname", :roles #{"group/r1"}} "user/uname group/r1"
                         {:username "user/uname", :roles #{"group/r1" "group/r2"}} "user/uname group/r1 group/r2"
                         {:username "user/uname", :roles #{"group/r1" "group/r2"}, :session session}
                         (str "user/uname group/r1 group/r2 " session)))

(deftest check-identity-map
  (let [anon-map {:current         "group/nuvla-anon"
                  :authentications {"group/nuvla-anon" {:roles #{"group/nuvla-anon"}}}}]
    (are [expected v] (= expected (create-identity-map v))
                      anon-map nil
                      anon-map [nil nil]
                      anon-map [nil []]

                      {:current         "group/nuvla-anon"
                       :authentications {"group/nuvla-anon" {:roles #{"group/roles" "group/nuvla-anon"}}}}
                      [nil ["group/roles"]]

                      {:current         "user/uname"
                       :authentications {"user/uname" {:identity "user/uname"
                                                       :roles    #{"group/nuvla-anon"}}}}
                      ["user/uname" []]

                      {:current         "user/uname"
                       :authentications {"user/uname" {:identity "user/uname"
                                                       :roles    #{"group/r1" "group/nuvla-anon"}}}}
                      ["user/uname" ["group/r1"]]

                      {:current         "user/uname"
                       :authentications {"user/uname" {:identity "user/uname"
                                                       :roles    #{"group/r1" "group/r2" "group/nuvla-anon"}}}}
                      ["user/uname" ["group/r1" "group/r2"]])))

(deftest check-handler
  (let [handler (wrap-authn-info-header identity)
        anon-map {:current         "group/nuvla-anon"
                  :authentications {"group/nuvla-anon" {:roles #{"group/nuvla-anon"}}}}]
    (are [expected request] (= expected (:identity (handler request)))
                            anon-map {}
                            anon-map {:headers {"header-1" "value"}}
                            anon-map {:headers {authn-info-header nil}}
                            anon-map {:headers {authn-info-header ""}}

                            {:current         "user/uname"
                             :authentications {"user/uname" {:identity "user/uname"
                                                             :roles    #{"group/nuvla-anon"}}}}
                            {:headers {authn-info-header "user/uname"}}

                            {:current         "user/uname"
                             :authentications {"user/uname" {:identity "user/uname"
                                                             :roles    #{"group/r1" "group/nuvla-anon"}}}}
                            {:headers {authn-info-header "user/uname group/r1"}}

                            {:current         "user/uname"
                             :authentications {"user/uname" {:identity "user/uname"
                                                             :roles    #{"group/r1" "group/r2" "group/nuvla-anon"}}}}
                            {:headers {authn-info-header "user/uname group/r1 group/r2"}}

                            {:current         "user/uname2"
                             :authentications {"user/uname2" {:identity "user/uname2"
                                                              :roles    #{"group/nuvla-anon"}}}}
                            {:cookies {authn-cookie cookie-id}}

                            {:current         "user/uname2"
                             :authentications {"user/uname2" {:identity "user/uname2"
                                                              :roles    #{"group/nuvla-user" "group/alpha-role"
                                                                          session-a "group/nuvla-anon"}}}}
                            {:cookies {authn-cookie cookie-id-roles}}

                            {:current         "user/uname"
                             :authentications {"user/uname" {:identity "user/uname"
                                                             :roles    #{"group/r1" "group/r2" "group/nuvla-anon"}}}}
                            {:headers {authn-info-header "user/uname group/r1 group/r2"}
                             :cookies {authn-cookie cookie-id-roles}})))
