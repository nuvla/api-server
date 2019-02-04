(ns com.sixsq.slipstream.auth.utils.db-test
  (:refer-clojure :exclude [update])
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.auth.internal :as ia]
    [com.sixsq.slipstream.auth.test-helper :as th]
    [com.sixsq.slipstream.auth.utils.db :as db]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.user.user-identifier-utils :as uiu]))


(use-fixtures :each ltu/with-test-server-fixture)


(deftest test-user-creation-standard-username
  (let [short-id "st"
        long-id "485879@vho-switchaai.chhttps://aai-logon.vho-switchaai.ch/idp/shibboleth!https://fed-id.nuv.la/samlbridge/module.php/saml/sp/metadata.php/sixsq-saml-bridge!uays4u2/dk2qefyxzsv9uiicv+y="]
    (doseq [id #{short-id long-id}]
      (is (= id (db/create-user! {:authn-method "github"
                                  :authn-login  id
                                  :email        "st@s.com"
                                  :roles        "alpha-role, beta-role"
                                  :firstname    "first"
                                  :lastname     "last"
                                  :organization "myorg"}))))))


(deftest test-user-creation-standard-username-oidc
  (let [identifier "st"]
    (is (= identifier (db/create-user! {:authn-method "oidc"
                                        :instance     "instance"
                                        :authn-login  identifier
                                        :email        "st@s.com"
                                        :roles        "alpha-role, beta-role"
                                        :firstname    "first"
                                        :lastname     "last"
                                        :organization "myorg"})))))


(deftest test-user-creation-uuid
  (let [uuid (u/random-uuid)]
    (is (= uuid (db/create-user! {:authn-method   "github"
                                  :authn-login    uuid
                                  :external-login "st"
                                  :email          "st@s.com"
                                  :roles          "alpha-role, beta-role"
                                  :firstname      "first"
                                  :lastname       "last"
                                  :organization   "myorg"})))

    (let [usernames (db/existing-user-names)
          user (db/get-user (first usernames))]

      (is (= 1 (count usernames)))
      (is (= "alpha-role, beta-role" (:roles user)))
      (is (= false (:deleted user)))
      (is (= "st@s.com" (:emailAddress user)))
      (is (= false (:isSuperUser user)))
      (is (= uuid (:username user)))
      (is (= "ACTIVE" (:state user)))
      (is (= "first" (:firstName user)))
      (is (= "last" (:lastName user)))
      (is (:password user))
      (is (:created user))
      (is (= "USER ANON" (db/find-roles-for-username "st")))))


  (is (= "st" (db/create-user! {:authn-method "github"
                                :authn-login  "st"
                                :email        "st@s.com"
                                :roles        "alpha-role, beta-role"
                                :firstname    "first"
                                :lastname     "last"
                                :organization "myorg"}))))


(deftest test-no-creation-on-existing-user
  (let [user-info {:authn-login    "stef"
                   :authn-method   "github"
                   :email          "st@s.com"
                   :external-login "stef"}]

    (th/add-user-for-test! {:username     "stef"
                            :password     "secret"
                            :emailAddress "st@s.com"})

    (is (= "stef" (db/create-user! user-info)))
    (is (nil? (db/create-user! (assoc user-info :fail-on-existing? true))))))


(defn create-users
  [n]
  (doseq [i (range n)]
    (let [name (str "foo_" i)
          user {:id           (str "user/" name)
                :username     name
                :password     "12345"
                :emailAddress "a@b.c"}]
      (th/add-user-for-test! user))))


(deftest test-existing-user-names
  (is (empty? (db/existing-user-names)))
  (create-users 3)
  (is (= 3 (count (db/existing-user-names)))))



(deftest test-users-by-email-skips-deleted
  (th/add-user-for-test! {:username     "jack"
                          :password     "123456"
                          :emailAddress "jack@sixsq.com"
                          :state        "DELETED"})

  (is (= #{} (db/find-usernames-by-email "unknown@xxx.com")))
  (is (= #{} (db/find-usernames-by-email "jack@sixsq.com"))))


(deftest test-users-by-email
  (th/add-user-for-test! {:username     "jack"
                          :password     "123456"
                          :emailAddress "jack@sixsq.com"})
  (th/add-user-for-test! {:username     "joe"
                          :password     "123456"
                          :emailAddress "joe@sixsq.com"})
  (th/add-user-for-test! {:username     "joe-alias"
                          :password     "123456"
                          :emailAddress "joe@sixsq.com"})

  (is (= #{} (db/find-usernames-by-email "unknown@xxx.com")))
  (is (= #{"jack"} (db/find-usernames-by-email "jack@sixsq.com")))
  (is (= #{"joe" "joe-alias"} (db/find-usernames-by-email "joe@sixsq.com"))))


(deftest test-users-by-authn-skips-deleted-legacy
  (th/add-user-for-test! {:username     "joe-slipstream"
                          :password     "123456"
                          :emailAddress "joe@sixsq.com"
                          :githublogin  "joe"
                          :state        "DELETED"})
  (is (nil? (uiu/find-username-by-identifier :github nil "joe"))))


(deftest test-users-by-authn-skips-deleted
  (th/add-user-for-test! {:username     "joe-slipstream"
                          :password     "123456"
                          :emailAddress "joe@sixsq.com"
                          :state        "DELETED"})
  (uiu/add-user-identifier! "joe-slipstream" :github "joe" nil)
  (is (nil? (uiu/find-username-by-identifier :github nil "joe"))))


(deftest test-users-by-authn-legacy
  (th/add-user-for-test! {:username     "joe-slipstream"
                          :password     "123456"
                          :emailAddress "joe@sixsq.com"
                          :githublogin  "joe"})

  (th/add-user-for-test! {:username     "jack-slipstream"
                          :password     "123456"
                          :emailAddress "jack@sixsq.com"
                          :githublogin  "jack"})

  (th/add-user-for-test! {:username     "alice-slipstream"
                          :password     "123456"
                          :emailAddress "alice@sixsq.com"})

  (is (nil? (uiu/find-username-by-identifier :github nil "unknownid")))
  (is (= "joe-slipstream" (uiu/find-username-by-identifier :github nil "joe"))))


(deftest test-users-by-authn
  (th/add-user-for-test! {:username     "joe-slipstream"
                          :password     "123456"
                          :emailAddress "joe@sixsq.com"})
  (uiu/add-user-identifier! "joe-slipstream" :github "joe" nil)

  (th/add-user-for-test! {:username     "jack-slipstream"
                          :password     "123456"
                          :emailAddress "jack@sixsq.com"})
  (uiu/add-user-identifier! "jack-slipstream" :oidc "jack" "my-instance")

  (th/add-user-for-test! {:username     "william-slipstream"
                          :password     "123456"
                          :emailAddress "william@sixsq.com"})
  (uiu/add-user-identifier! "william-slipstream" :some-method "bill" "some-instance")


  (th/add-user-for-test! {:username     "alice-slipstream"
                          :password     "123456"
                          :emailAddress "alice@sixsq.com"})

  (is (nil? (uiu/find-username-by-identifier :github nil "unknownid")))
  (is (= "joe-slipstream" (uiu/find-username-by-identifier :github nil "joe")))
  (is (= "jack-slipstream" (uiu/find-username-by-identifier :oidc "my-instance" "jack")))
  (is (= "william-slipstream" (uiu/find-username-by-identifier :some-method "some-instance" "bill"))))


(deftest test-users-by-authn-detect-inconsistent-data-legacy
  (th/add-user-for-test! {:username     "joe1-slipstream"
                          :password     "123456"
                          :emailAddress "jane@example.org"
                          :firstName    "Jane"
                          :lastName     "Tester"
                          :githublogin  "joe"})
  (th/add-user-for-test! {:username     "joe2-slipstream"
                          :password     "123456"
                          :emailAddress "jane@example.org"
                          :firstName    "Jane"
                          :lastName     "Tester"
                          :githublogin  "joe"})
  (is (thrown-with-msg? Exception #"one result for joe"
                        (uiu/find-username-by-identifier :github nil "joe"))))

(deftest check-user-exists?
  (let [test-username "some-long-random-user-name-that-does-not-exist"
        test-username-deleted (str test-username "-deleted")]
    (is (false? (db/user-exists? test-username)))
    (is (false? (db/user-exists? test-username-deleted)))
    (th/add-user-for-test! {:username     test-username
                            :password     "password"
                            :emailAddress "jane@example.org"
                            :firstName    "Jane"
                            :lastName     "Tester"
                            :state        "ACTIVE"})
    (th/add-user-for-test! {:username     test-username-deleted
                            :password     "password"
                            :emailAddress "jane@example.org"
                            :firstName    "Jane"
                            :lastName     "Tester"
                            :state        "DELETED"})
    (is (true? (db/user-exists? test-username)))

    ;; users in any state exist, but should not be listed as active
    (is (true? (db/user-exists? test-username-deleted)))
    (is (nil? (db/get-active-user-by-name test-username-deleted)))))


(deftest test-find-password-for-username
  (let [username "testuser"
        password "password"
        pass-hash (ia/hash-password password)
        user {:username username
              :password password}]
    (th/add-user-for-test! user)
    (is (= pass-hash (db/find-password-for-username username)))))


(deftest test-find-roles-for-username
  (let [username "testuser"
        user {:username    username
              :password    "password"
              :isSuperUser false}]
    (th/add-user-for-test! user)
    (is (= "USER ANON" (db/find-roles-for-username username)))))

