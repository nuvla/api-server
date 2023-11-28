(ns sixsq.nuvla.server.resources.common.std-crud-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db-impl]
    [sixsq.nuvla.server.resources.common.std-crud :as t])
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
