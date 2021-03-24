(ns sixsq.nuvla.auth.cookies-test
  (:refer-clojure :exclude [update])
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [environ.core :as environ]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as codec]
    [sixsq.nuvla.auth.cookies :as t]
    [sixsq.nuvla.auth.env-fixture :as env-fixture]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.group :as group]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context group/resource-type))


(defn serialize-cookie-value
  "replaces the map cookie value with a serialized string"
  [{:keys [value] :as cookie}]
  (assoc cookie :value (codec/form-encode value)))


(defn damaged-cookie-value
  "replaces the map cookie value with a serialized string, but modifies it to make it invalid"
  [{:keys [value] :as cookie}]
  (assoc cookie :value (str (codec/form-encode value) "-INVALID")))


(deftest revoked-cookie-ok
  (let [revoked (t/revoked-cookie)]
    (is (map? revoked))
    (is (= "INVALID" (get-in revoked [:value]))))
  (let [k       "cookie.name"
        revoked (t/revoked-cookie k)]
    (is (map? revoked))
    (is (= "INVALID" (get-in revoked [k :value])))))


(deftest claims-cookie-ok
  (with-redefs [environ/env env-fixture/env-map]
    (let [claims       {:alpha "a", :beta "b", :gamma 3}
          cookie       (t/create-cookie claims)
          k            "cookie.name"
          named-cookie (t/create-cookie claims k)]
      (is (map? cookie))
      (is (not= "INVALID" (:value cookie)))
      (is (:value cookie))
      (is (map? named-cookie))
      (is (not= "INVALID" (get-in named-cookie [k :value])))
      (is (get-in named-cookie [k :value])))))


(deftest check-extract-cookie-info
  (with-redefs [environ/env env-fixture/env-map]
    (let [cookie-info {:user-id "user"
                       :claims  "role1 role2"
                       :session "session"}]

      (is (nil? (t/extract-cookie-info nil)))
      (is (nil? (-> cookie-info
                    t/create-cookie
                    damaged-cookie-value
                    t/extract-cookie-info)))

      (let [cookie-info-extracted (-> cookie-info
                                      t/create-cookie
                                      serialize-cookie-value
                                      t/extract-cookie-info)]
        (is (= {:claims  "role1 role2"
                :session "session"
                :user-id "user"} (dissoc cookie-info-extracted :exp)))
        (is (some? (:exp cookie-info-extracted)))))))


(deftest check-collect-groups-for-user
  (with-redefs [db/query (constantly nil)]
    (is (= #{} (t/collect-groups-for-user "user/aa2f41a3-c54c-fce8-32d2-0324e1c32e22")))))


(deftest check-collect-groups-for-user-with-real-groups

  (let [app             (ltu/ring-app)
        session-json    (content-type (session app) "application/json")
        session-admin   (header session-json authn-info-header "user/super user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")

        admin-group-uri (str p/service-context group/resource-type "/nuvla-admin")

        user-id         "user/aa2f41a3-c54c-fce8-32d2-0324e1c32e22"

        test-group-tpl  {:template {:group-identifier "test-group"}}

        test-group-uri  (str p/service-context group/resource-type "/test-group")]

    ;; create a group and add the user
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str test-group-tpl))
        (ltu/body->edn)
        (ltu/is-status 201))

    ;; add user as an administrator
    (let [users [user-id]

          {existing-users :users :as body} (-> session-admin
                                               (request admin-group-uri)
                                               (ltu/body->edn)
                                               (ltu/body))]

      (-> session-admin
          (request admin-group-uri
                   :request-method :put
                   :body (json/write-str
                           (assoc body :users (vec (distinct (concat existing-users users))))))
          (ltu/body->edn)
          (ltu/is-status 200))

      (-> session-admin
          (request test-group-uri
                   :request-method :put
                   :body (json/write-str {:users users}))
          (ltu/body->edn)
          (ltu/is-status 200))

      (-> session-admin
          (request test-group-uri)
          (ltu/body->edn)
          (ltu/is-status 200))


      ;; check that the changes have been picked up
      (let [result (t/collect-groups-for-user user-id)]
        (is (= #{"group/nuvla-admin" "group/test-group"}
               result))))))
