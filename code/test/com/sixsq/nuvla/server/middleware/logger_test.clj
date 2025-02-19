(ns com.sixsq.nuvla.server.middleware.logger-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
            [com.sixsq.nuvla.server.middleware.logger :as t]))

(def start 1450701200947)
(def end 1450701203089)
(def test-url "api/resource")

(defn req
  [{:keys [nuvla-authn-info query-string]}]
  {:request-method :get
   :uri            test-url
   :headers        {authn-info-header nuvla-authn-info}
   :query-string   query-string
   :content-type   "application/json"
   :body           "body-content"})

(deftest request-log-data
  (is (= {:authn-info   {:active-claim "joe"
                         :claims       #{"group/nuvla-anon"
                                         "joe"}
                         :user-id      "joe"}
          :content-type "application/json"
          :method       "GET"
          :query-string "c=3"
          :uri          "api/resource"} (t/request-log-data
                                          (req {:nuvla-authn-info "joe" :query-string "c=3"}))))
  (is (= {:authn-info   {:active-claim "joe"
                         :claims       #{"group/nuvla-anon"
                                         "joe"}
                         :user-id      "joe"}
          :content-type "application/json"
          :method       "GET"
          :uri          "api/resource"} (t/request-log-data
                                          (req {:nuvla-authn-info "joe"}))))

  (is (= {:authn-info   {:active-claim "joe"
                         :claims       #{"R1"
                                         "R2"
                                         "group/nuvla-anon"
                                         "joe"}
                         :user-id      "joe"}
          :content-type "application/json"
          :method       "GET"
          :uri          "api/resource"} (t/request-log-data
                                          (req {:nuvla-authn-info "joe joe R1 R2"}))))

  (testing "log does not display password"
    (is (= "a=1&b=2" (:query-string
                       (t/request-log-data
                         (req {:nuvla-authn-info "super super group/nuvla-admin"
                               :query-string     "a=1&password=secret&b=2"})))))))


(deftest response-log-data
  (is (= {:authn-info   {:active-claim "joe"
                         :claims       #{"R1"
                                         "R2"
                                         "group/nuvla-anon"
                                         "joe"}
                         :user-id      "joe"}
          :content-type "application/json"
          :duration-ms  2142
          :method       "GET"
          :status       200
          :uri          "api/resource"}
         (t/response-log-data {:authn-info   {:active-claim "joe"
                                              :claims       #{"R1"
                                                              "R2"
                                                              "group/nuvla-anon"
                                                              "joe"}
                                              :user-id      "joe"}
                               :content-type "application/json"
                               :method       "GET"
                               :uri          "api/resource"} start end 200)))
  (is (= {:content-type "application/json"
          :duration-ms  2142
          :method       "POST"
          :status       303
          :uri          "api/session"}
         (t/response-log-data {:content-type "application/json"
                               :method       "POST"
                               :uri          "api/session"} start end 303)))
  (is (= {:content-type "application/json"
          :duration-ms  2142
          :method       "GET"
          :status       404
          :uri          "api/foo"}
         (t/response-log-data {:content-type "application/json"
                               :method       "GET"
                               :uri          "api/foo"} start end 404))))
