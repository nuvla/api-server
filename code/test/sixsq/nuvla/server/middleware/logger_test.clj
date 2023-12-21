(ns sixsq.nuvla.server.middleware.logger-test
  (:require [clojure.test :refer [deftest is]]
            [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
            [sixsq.nuvla.server.middleware.logger :as t]))


(def start 1450701200947)


(def end 1450701203089)


(def test-url "api/resource")


(defn req
  [{:keys [nuvla-authn-info query-string]}]
  {:request-method :get
   :uri            test-url
   :headers        {authn-info-header nuvla-authn-info}
   :query-string   query-string
   :body           "body-content"})


(defn is-request-formatted
  [expected & {:as req-params}]
  (is (= expected (t/format-request (req req-params)))))


(defn is-response-formatted
  [expected & {:keys [:start :end :status] :as response-params}]
  (is (= expected (t/format-response
                    (t/format-request (req response-params))
                    {:status status} start end))))


(deftest log-does-not-display-password
  (is-request-formatted (str "GET " test-url " [super - group/nuvla-admin,group/nuvla-anon,super] ?a=1&b=2")
                        :nuvla-authn-info "super super group/nuvla-admin" :query-string "a=1&password=secret&b=2"))


(deftest test-formatting
  (is-response-formatted (format (str "200 (%d ms) GET " test-url " [super - group/nuvla-admin,group/nuvla-anon,super] ?a=1&b=2") (- end start))
                         :nuvla-authn-info "super super group/nuvla-admin" :query-string "a=1&password=secret&b=2"
                         :start start :end end :status 200)

  (is-request-formatted (str "GET " test-url " [joe - group/nuvla-anon,joe] ?c=3")
                        :nuvla-authn-info "joe" :query-string "c=3")

  (is-request-formatted (str "GET " test-url " [joe - group/nuvla-anon,joe] ?")
                        :nuvla-authn-info "joe")

  (is-request-formatted (str "GET " test-url " [joe - R1,R2,group/nuvla-anon,joe] ?")
                        :nuvla-authn-info "joe joe R1 R2"))


