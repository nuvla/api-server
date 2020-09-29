(ns sixsq.nuvla.server.middleware.logger-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.middleware.logger :as t]))


(def start 1450701200947)


(def end 1450701203089)


(def test-url "api/resource")


(defn header-authn-info
  [user roles]
  {"nuvla-authn-info" (str/join " " (concat [user] roles))})


(defn req
  [{:keys [user-id claims query-string]}]
  {:request-method :get
   :uri            test-url
   :nuvla/authn    {:user-id user-id
                    :active-claim user-id
                    :claims  claims}
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
  (is-request-formatted (str "GET " test-url " [super - group/nuvla-admin] ?a=1&b=2")
                        :user-id "super" :claims #{"group/nuvla-admin"} :query-string "a=1&password=secret&b=2"))


(deftest test-formatting
  (is-response-formatted (format (str "200 (%d ms) GET " test-url " [super - group/nuvla-admin] ?a=1&b=2") (- end start))
                         :user-id "super" :claims #{"group/nuvla-admin"} :query-string "a=1&password=secret&b=2"
                         :start start :end end :status 200)

  (is-request-formatted (str "GET " test-url " [joe - ] ?c=3")
                        :user-id "joe" :query-string "c=3")

  (is-request-formatted (str "GET " test-url " [joe - ] ?")
                        :user-id "joe")

  (is-request-formatted (str "GET " test-url " [joe - R1,R2] ?")
                        :user-id "joe" :claims #{"R1" "R2"}))


