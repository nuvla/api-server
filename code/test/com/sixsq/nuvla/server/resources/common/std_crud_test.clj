(ns com.sixsq.nuvla.server.resources.common.std-crud-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.db.impl :as db-impl]
    [com.sixsq.nuvla.server.resources.common.std-crud :as t])
  (:import (clojure.lang ExceptionInfo)))


(deftest resolve-href-keep-with-nil-href
  (with-redefs [db-impl/retrieve (fn [_ & _] nil)]
    (is (thrown-with-msg? ExceptionInfo (re-pattern t/href-not-found-msg)
                          (t/resolve-href-keep {:href "foo"} {}))))
  (with-redefs [db-impl/retrieve         (fn [_ & _] {:dummy "resource"})
                a/throw-cannot-view-data (fn [_ _] (throw (ex-info "" {:status 403, :other "BAD"})))]
    (is (thrown-with-msg? ExceptionInfo (re-pattern t/href-not-accessible-msg)
                          (t/resolve-href-keep {:href "foo"} {})))
    (try
      (t/resolve-href-keep {:href "foo"} {})
      (catch Exception ex
        (let [data (ex-data ex)]
          (is (nil? (:other data)))
          (is (= 400 (:status data))))))))

(deftest json-patch-issue-move-vector
  (let [ne-status {"tasks" [{}
                            {"NetworksAttachments" [{"Addresses" []}]}
                            {"NetworksAttachments" [{"Addresses" ["10.0.2.87/24"]}]}]}
        ne-patch  [{"op"   "move",
                    "from" "/tasks/2/NetworksAttachments/0/Addresses/0",
                    "path" "/tasks/1/NetworksAttachments/0/Addresses/0"}]]
    (is (= {:tasks [{}
                    {:NetworksAttachments [{:Addresses ["10.0.2.87/24"]}]}
                    {:NetworksAttachments [{:Addresses []}]}]}
           (t/json-safe-patch ne-status ne-patch)))))
