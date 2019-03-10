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

(def cookie-id (serialize-cookie-value (cookies/claims-cookie {:username "uname2"})))
(def cookie-id-roles (serialize-cookie-value
                       (cookies/claims-cookie {:username "uname2"
                                               :roles    "USER alpha-role"
                                               :session  session-a})))

(deftest check-is-session?
  (are [expected s] (= expected (is-session? s))
                    nil nil
                    nil ""
                    nil "USER"
                    session session
                    session-a session-a))

(deftest check-extract-authn-info
  (are [expected header] (= expected (extract-authn-info {:headers {authn-info-header header}}))
                         nil nil
                         nil ""
                         ["uname" #{}] "uname"
                         ["uname" #{}] "  uname"
                         ["uname" #{"r1"}] "uname r1"
                         ["uname" #{"r1"}] "  uname r1"
                         ["uname" #{"r1"}] "uname r1  "
                         ["uname" #{"r1" "r2"}] "uname r1 r2"))

(deftest check-extract-info
  (are [expected request] (= expected (extract-info request))
                          nil {}
                          ["uname" #{"r1"}] {:headers {authn-info-header "uname r1"}}
                          ["uname2" #{"USER" "alpha-role" session-a}] {:cookies {authn-cookie cookie-id-roles}}
                          ["uname" #{"r1"}] {:headers {authn-info-header "uname r1"}
                                             :cookies {authn-cookie cookie-id-roles}}))

(deftest check-extract-header-claims
  (are [expected header] (= expected (extract-header-claims {:headers {authn-info-header header}}))
                         nil nil
                         nil ""
                         {:username "uname"} "uname"
                         {:username "uname", :roles #{"r1"}} "uname r1"
                         {:username "uname", :roles #{"r1" "r2"}} "uname r1 r2"
                         {:username "uname", :roles #{"r1" "r2"}, :session session} (str "uname r1 r2 " session)))

(deftest check-identity-map
  (let [anon-map {:current         "ANON"
                  :authentications {"ANON" {:roles #{"ANON"}}}}]
    (are [expected v] (= expected (create-identity-map v))
                      anon-map nil
                      anon-map [nil nil]
                      anon-map [nil []]

                      {:current         "ANON"
                       :authentications {"ANON" {:roles #{"roles" "ANON"}}}}
                      [nil ["roles"]]

                      {:current         "uname"
                       :authentications {"uname" {:identity "uname"
                                                  :roles    #{"ANON"}}}}
                      ["uname" []]

                      {:current         "uname"
                       :authentications {"uname" {:identity "uname"
                                                  :roles    #{"r1" "ANON"}}}}
                      ["uname" ["r1"]]

                      {:current         "uname"
                       :authentications {"uname" {:identity "uname"
                                                  :roles    #{"r1" "r2" "ANON"}}}}
                      ["uname" ["r1" "r2"]])))

(deftest check-handler
  (let [handler (wrap-authn-info-header identity)
        anon-map {:current         "ANON"
                  :authentications {"ANON" {:roles #{"ANON"}}}}]
    (are [expected request] (= expected (:identity (handler request)))
                            anon-map {}
                            anon-map {:headers {"header-1" "value"}}
                            anon-map {:headers {authn-info-header nil}}
                            anon-map {:headers {authn-info-header ""}}

                            {:current         "uname"
                             :authentications {"uname" {:identity "uname"
                                                        :roles    #{"ANON"}}}}
                            {:headers {authn-info-header "uname"}}

                            {:current         "uname"
                             :authentications {"uname" {:identity "uname"
                                                        :roles    #{"r1" "ANON"}}}}
                            {:headers {authn-info-header "uname r1"}}

                            {:current         "uname"
                             :authentications {"uname" {:identity "uname"
                                                        :roles    #{"r1" "r2" "ANON"}}}}
                            {:headers {authn-info-header "uname r1 r2"}}

                            {:current         "uname2"
                             :authentications {"uname2" {:identity "uname2"
                                                         :roles    #{"ANON"}}}}
                            {:cookies {authn-cookie cookie-id}}

                            {:current         "uname2"
                             :authentications {"uname2" {:identity "uname2"
                                                         :roles    #{"USER" "alpha-role" session-a "ANON"}}}}
                            {:cookies {authn-cookie cookie-id-roles}}

                            {:current         "uname"
                             :authentications {"uname" {:identity "uname"
                                                        :roles    #{"r1" "r2" "ANON"}}}}
                            {:headers {authn-info-header "uname r1 r2"}
                             :cookies {authn-cookie cookie-id-roles}})))
