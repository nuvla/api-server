(ns sixsq.nuvla.server.resources.user.user-identifier-utils-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [sixsq.nuvla.auth.test-helper :as th]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]))


(use-fixtures :once ltu/with-test-server-fixture)


(deftest create-user-identifier
  (let [name (ltu/random-string "user-")
        user {:id           (str "user/" name)
              :username     name
              :password     "12345"
              :emailAddress "a@b.c"}
        authn-method :sample-method
        external-login (ltu/random-string "external-")
        instance (ltu/random-string "instance-")
        identifier (uiu/generate-identifier authn-method external-login instance)
        user-response (th/add-user-for-test! user)
        user-id (-> user-response :body :resource-id)
        user-identifier-response (uiu/add-user-identifier! name authn-method external-login instance)
        user-identifier-id (-> user-identifier-response :body :resource-id)]
    (is (= 201 (:status user-identifier-response)))
    (is (= (u/md5 identifier) (-> user-identifier-id (str/split #"/") second)))

    (let [resource (crud/retrieve-by-id user-identifier-id)]
      (is (= identifier (:identifier resource)))
      (is (= {:href user-id} (:user resource))))))


(deftest identities-for-user
  (let [name (ltu/random-string "user-")
        external-login (ltu/random-string "external-")
        user {:id           (str "user/" name)
              :username     name
              :password     "12345"
              :emailAddress "a@b.c"}
        _ (th/add-user-for-test! user)
        authn-methods #{:some-method :other-method :third-method}]

    (doseq [authn-method authn-methods]
      (uiu/add-user-identifier! name authn-method external-login nil))

    (let [results (uiu/find-identities-by-user (:id user))]
      (is (= (count authn-methods) (count results)))
      (is (= (set (map :identifier results))
             (set (map #(uiu/generate-identifier % external-login nil) authn-methods)))))))


(deftest identities-for-user-with-instance
  (let [name (ltu/random-string "user-")
        instance (ltu/random-string "instance-")
        external-login (ltu/random-string "external-")
        user {:id           (str "user/" name)
              :username     name
              :password     "12345"
              :emailAddress "a@b.c"}
        _ (th/add-user-for-test! user)
        authn-methods #{:some-method :other-method :third-method}]
    (doseq [authn-method authn-methods]
      (uiu/add-user-identifier! name authn-method external-login instance))

    (let [results (uiu/find-identities-by-user (:id user))]
      (is (= 1 (count results)))
      (is (= (set (map :identifier results)) (set (map #(uiu/generate-identifier % external-login instance) authn-methods)))))))


(deftest double-user-identifier
  (let [name (ltu/random-string "user-")
        name2 (ltu/random-string "other-")
        user {:id           (str "user/" name)
              :username     name
              :password     "12345"
              :emailAddress "a@b.c"}
        user2 (assoc user :username name2)
        authn-method :sample-method
        external-login (ltu/random-string "#*$(%*#*__12345-")
        instance (ltu/random-string "instance-")
        _ (th/add-user-for-test! user2)
        user-identifier-response (uiu/add-user-identifier! name authn-method external-login instance)
        user-identifier-response2 (uiu/add-user-identifier! name2 authn-method external-login instance)]
    (is (= 201 (:status user-identifier-response)))
    (is (= 409 (:status user-identifier-response2)))))
