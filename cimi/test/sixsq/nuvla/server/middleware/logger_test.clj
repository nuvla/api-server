(ns sixsq.nuvla.server.middleware.logger-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.middleware.logger :refer :all]))

(def start 1450701200947)
(def end 1450701203089)

(def test-url "api/resource")

(defn header-authn-info
  [user roles]
  {"slipstream-authn-info" (str/join " " (concat [user] roles))})

(defn req
  [{:keys [user roles query-string]}]
  {:request-method :get
   :uri            test-url
   :headers        (header-authn-info user roles)
   :query-string   query-string
   :body           "body-content"})

(defn is-request-formatted
  [expected & {:as req-params}]
  (is (= expected (format-request (req req-params)))))

(defn is-response-formatted
  [expected & {:keys [:start :end :status] :as response-params}]
  (is (= expected (format-response
                    (format-request (req response-params))
                    {:status status} start end))))

(deftest log-does-not-display-password
  (is-request-formatted (str "GET " test-url " [super/ADMIN] ?a=1&b=2")
                        :user "super" :roles ["ADMIN"] :query-string "a=1&password=secret&b=2"))

(deftest test-formatting
  (is-response-formatted (format (str "200 (%d ms) GET " test-url " [super/ADMIN] ?a=1&b=2") (- end start))
                         :user "super" :roles ["ADMIN"] :query-string "a=1&password=secret&b=2"
                         :start start :end end :status 200)

  (is-request-formatted (str "GET " test-url " [joe/] ?c=3")
                        :user "joe" :query-string "c=3")

  (is-request-formatted (str "GET " test-url " [joe/] ?")
                        :user "joe")

  (is-request-formatted (str "GET " test-url " [joe/R1,R2] ?")
                        :user "joe" :roles ["R1" "R2"]))


