(ns com.sixsq.nuvla.auth.cookies-test
  (:refer-clojure :exclude [update])
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [environ.core :as environ]
    [ring.util.codec :as codec]
    [com.sixsq.nuvla.auth.cookies :as t]
    [com.sixsq.nuvla.auth.env-fixture :as env-fixture]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.group :as group]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


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
