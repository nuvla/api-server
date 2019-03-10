(ns sixsq.nuvla.auth.utils.db-test
  (:refer-clojure :exclude [update])
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.auth.internal :as ia]
    [sixsq.nuvla.auth.test-helper :as th]
    [sixsq.nuvla.auth.utils.db :as db]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]))


(use-fixtures :once ltu/with-test-server-fixture)


(deftest test-user-creation-standard-username
  (let [short-id (ltu/random-string "st-")
        long-id (ltu/random-string "485879@vho-switchaai.chhttps://aai-logon.vho-switchaai.ch/idp/shibboleth!https://fed-id.nuv.la/samlbridge/module.php/saml/sp/metadata.php/sixsq-saml-bridge!uays4u2/dk2qefyxzsv9uiicv+y=-")]
    (doseq [id #{short-id long-id}]
      (is (= id (db/create-user! {:authn-method "github"
                                  :authn-login  id
                                  :email        "st@s.com"
                                  :firstname    "first"
                                  :lastname     "last"}))))))


(deftest test-user-creation-standard-username-oidc
  (let [identifier (ltu/random-string "st-")]
    (is (= identifier (db/create-user! {:authn-method "oidc"
                                        :instance     "instance"
                                        :authn-login  identifier
                                        :email        "st@s.com"
                                        :full-name    "full name"})))))


(deftest test-user-creation-uuid
  (let [uuid (u/random-uuid)
        external (ltu/random-string "external-")
        email (str external "@sixsq.com")]
    (is (= uuid (db/create-user! {:authn-method   "github"
                                  :authn-login    uuid
                                  :external-login external
                                  :email          email
                                  :full-name      "full name"})))

    (let [usernames (db/existing-user-names)
          user (db/get-user uuid)]

      (is (contains? usernames uuid))
      (is (false? (:deleted user)))
      (is (= email (:emailAddress user)))
      (is (false? (:isSuperUser user)))
      (is (= uuid (:username user)))
      (is (= "ACTIVE" (:state user)))
      (is (= "full name" (:full-name user)))
      (is (:password user))
      (is (:created user))
      (is (= "USER ANON" (db/find-roles-for-username external)))))


  (is (= "st" (db/create-user! {:authn-method "github"
                                :authn-login  "st"
                                :email        "st@s.com"
                                :roles        "alpha-role, beta-role"
                                :firstname    "first"
                                :lastname     "last"}))))


(deftest test-no-creation-on-existing-user
  (let [joe (ltu/random-string "joe-")
        user-info {:authn-login    joe
                   :authn-method   "github"
                   :email          (str joe "@sixsq.com")
                   :external-login "stef"}]

    (th/add-user-for-test! {:username     joe
                            :password     "secret"
                            :emailAddress "st@s.com"})

    (is (= joe (db/create-user! user-info)))
    (is (nil? (db/create-user! (assoc user-info :fail-on-existing? true))))))


(deftest test-users-by-email-skips-deleted
  (let [jack (ltu/random-string "jack-")]
    (th/add-user-for-test! {:username     jack
                            :password     "123456"
                            :emailAddress (str jack "@sixsq.com")
                            :state        "DELETED"})

    (is (= #{} (db/find-usernames-by-email (str (ltu/random-string) "@xxx.com"))))
    (is (= #{} (db/find-usernames-by-email (str jack "@sixsq.com"))))))


(deftest test-users-by-email
  (let [joe (ltu/random-string "joe-")
        jack (ltu/random-string "jack-")
        alias (ltu/random-string "alias-")]

    (th/add-user-for-test! {:username     jack
                            :password     "123456"
                            :emailAddress (str jack "@sixsq.com")})
    (th/add-user-for-test! {:username     joe
                            :password     "123456"
                            :emailAddress (str joe "@sixsq.com")})
    (th/add-user-for-test! {:username     alias
                            :password     "123456"
                            :emailAddress (str joe "@sixsq.com")})

    (is (= #{} (db/find-usernames-by-email (str (ltu/random-string) "@xxx.com"))))
    (is (= #{jack} (db/find-usernames-by-email (str jack "@sixsq.com"))))
    (is (= #{joe alias} (db/find-usernames-by-email (str joe "@sixsq.com"))))))


(deftest test-users-by-authn-skips-deleted-legacy
  (let [joe (ltu/random-string "joe-")]
    (th/add-user-for-test! {:username     joe
                            :password     "123456"
                            :emailAddress "joe@sixsq.com"
                            :state        "DELETED"})
    (is (nil? (uiu/find-username-by-identifier :github nil joe)))))


(deftest test-users-by-authn-skips-deleted
  (let [joe (ltu/random-string "joe-")]
    (th/add-user-for-test! {:username     joe
                            :password     "123456"
                            :emailAddress "joe@sixsq.com"
                            :state        "DELETED"})
    (uiu/add-user-identifier! joe :github joe nil)
    (is (nil? (uiu/find-username-by-identifier :github nil joe)))))


(deftest test-users-by-authn
  (let [joe (ltu/random-string "joe-")
        jack (ltu/random-string "jack-")
        william (ltu/random-string "william-")
        alice (ltu/random-string "alice-")]

    (th/add-user-for-test! {:username     joe
                            :password     "123456"
                            :emailAddress "joe@sixsq.com"})
    (uiu/add-user-identifier! joe :github joe nil)

    (th/add-user-for-test! {:username     jack
                            :password     "123456"
                            :emailAddress "jack@sixsq.com"})
    (uiu/add-user-identifier! jack :oidc jack "my-instance")

    (th/add-user-for-test! {:username     william
                            :password     "123456"
                            :emailAddress "william@sixsq.com"})
    (uiu/add-user-identifier! william :some-method william "some-instance")


    (th/add-user-for-test! {:username     alice
                            :password     "123456"
                            :emailAddress "alice@sixsq.com"})

    (is (nil? (uiu/find-username-by-identifier :github nil (ltu/random-string "unknown-"))))
    (is (= joe (uiu/find-username-by-identifier :github nil joe)))
    (is (= jack (uiu/find-username-by-identifier :oidc "my-instance" jack)))
    (is (= william (uiu/find-username-by-identifier :some-method "some-instance" william)))))


(deftest check-user-exists?
  (let [test-username (ltu/random-string "some-long-random-user-name-that-does-not-exist-")
        test-username-deleted (str test-username "-deleted")]
    (is (false? (db/user-exists? test-username)))
    (is (false? (db/user-exists? test-username-deleted)))
    (th/add-user-for-test! {:username     test-username
                            :password     "password"
                            :emailAddress "jane@example.org"
                            :full-name    "Jane Tester"
                            :state        "ACTIVE"})
    (th/add-user-for-test! {:username     test-username-deleted
                            :password     "password"
                            :emailAddress "jane@example.org"
                            :full-name    "Jane Tester"
                            :state        "DELETED"})
    (is (true? (db/user-exists? test-username)))

    ;; users in any state exist, but should not be listed as active
    (is (true? (db/user-exists? test-username-deleted)))
    (is (nil? (db/get-active-user-by-name test-username-deleted)))))


(deftest test-find-password-for-username
  (let [username (ltu/random-string "user-")
        password "password"
        pass-hash (ia/hash-password password)
        user {:username username
              :password password}]
    (th/add-user-for-test! user)
    (is (= pass-hash (db/find-password-for-username username)))))


(deftest test-find-roles-for-username
  (let [username (ltu/random-string "user-")
        user {:username    username
              :password    "password"
              :isSuperUser false}]
    (th/add-user-for-test! user)
    (is (= "USER ANON" (db/find-roles-for-username username)))))

