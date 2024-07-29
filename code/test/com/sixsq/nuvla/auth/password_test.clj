(ns com.sixsq.nuvla.auth.password-test
  (:require
    [buddy.hashers :as hashers]
    [clojure.test :refer [are deftest is use-fixtures]]
    [com.sixsq.nuvla.auth.password :as t]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.group :as group]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


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

