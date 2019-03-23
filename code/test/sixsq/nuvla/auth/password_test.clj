(ns sixsq.nuvla.auth.password-test
  (:require
    [buddy.hashers :as hashers]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [are deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.auth.password :as t]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.group :as group]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context group/resource-type))


(deftest check-user-id->user
  (is (nil? (t/user-id->user nil)))
  (is (nil? (t/user-id->user "user/unknown-user")))
  (is (nil? (t/user-id->user "bad-index/unknown-uuid"))))


(deftest check-user-active
  (is (nil? (t/check-user-active nil)))
  (is (nil? (t/check-user-active {})))
  (is (nil? (t/check-user-active {:state "NEW"})))
  (is (= {:state "ACTIVE"} (t/check-user-active {:state "ACTIVE"}))))


(deftest check-credential-id->credential
  (is (nil? (t/credential-id->credential nil)))
  (is (nil? (t/credential-id->credential "credential/unknown-credential")))
  (is (nil? (t/credential-id->credential "bad-index/unknown-uuid"))))


(deftest check-valid-password?
  (are [pwd hash expected] (is (= expected (t/valid-password? pwd hash)))
                           nil nil false
                           nil "bad-hash" false
                           "password" nil false
                           "password" "bad-hash" false
                           "password" (hashers/derive "password") true))


(deftest check-collect-groups-for-user
  (with-redefs [db/query (constantly nil)]
    (let [result (t/collect-groups-for-user "user/aa2f41a3-c54c-fce8-32d2-0324e1c32e22")]
      (= #{"USER" "ANON" "group/nuvla-user" "group/nuvla-anon"} (set (str/split result #"\s"))))))


(deftest check-collect-groups-for-user-with-real-groups

  (let [app (ltu/ring-app)
        session-json (content-type (session app) "application/json")
        session-admin (header session-json authn-info-header "root ADMIN USER ANON")

        admin-group-uri (str p/service-context group/resource-type "/nuvla-admin")

        user-id "user/aa2f41a3-c54c-fce8-32d2-0324e1c32e22"

        test-group-tpl {:template {:group-identifier "test-group"}}

        test-group-uri (str p/service-context group/resource-type "/test-group")]

    ;; create a group and add the user
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str test-group-tpl))
        (ltu/body->edn)
        (ltu/is-status 201))

    ;; add user as an administrator
    (let [users [user-id]

          test-group (-> session-admin
                         (request test-group-uri)
                         (ltu/body->edn)
                         :response
                         :body)

          {existing-users :users :as body} (-> session-admin
                                               (request admin-group-uri)
                                               (ltu/body->edn)
                                               :response
                                               :body)]

      (-> session-admin
          (request admin-group-uri
                   :request-method :put
                   :body (json/write-str (assoc body :users (vec (distinct (concat existing-users users))))))
          (ltu/body->edn)
          (ltu/is-status 200))

      (-> session-admin
          (request test-group-uri
                   :request-method :put
                   :body (json/write-str (assoc body :users users)))
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; check that the changes have been picked up
      (let [result (t/collect-groups-for-user user-id)]
        (= #{"ADMIN" "USER" "ANON" "group/nuvla-admin" "group/nuvla-user" "group/nuvla-anon" "group/test-group"}
           (set (str/split result #"\s")))))))
