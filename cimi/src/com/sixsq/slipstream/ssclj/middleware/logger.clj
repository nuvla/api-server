(ns com.sixsq.slipstream.ssclj.middleware.logger
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :as aih]))

(defn- display-querystring
  [request]
  (str "?" (-> request
               :query-string
               (or "")
               (str/replace #"&?password=([^&]*)" ""))))

(defn- display-authn-info
  [request]
  (let [[user roles] (aih/extract-info request)]
    (apply str "[" user "/" (str/join "," (sort roles)) "]")))

(defn- display-elapsed-time-millis
  [start current-time-millis]
  (str "(" (- current-time-millis start) " ms)"))

(defn- display-space-separated
  [& messages]
  (str/join " " messages))

(defn format-request
  [request]
  (display-space-separated
    (-> request :request-method name str/upper-case)
    (:uri request)
    (display-authn-info request)
    (display-querystring request)))

(defn format-response
  [formatted-request response start current-time-millis]
  (display-space-separated
    (:status response)
    (display-elapsed-time-millis start current-time-millis)
    formatted-request))

(defn log-response
  [status formatted-message]
  (cond
    (<= 100 status 399) (log/info formatted-message)
    (<= 400 status 499) (log/warn formatted-message)
    (<= 500 status 599) (log/error formatted-message)
    :else (log/error formatted-message)))

(defn wrap-logger
  "Logs both request and response e.g:
  2016-02-02 11:32:19,310 INFO  - GET /vms [no-authn-info] ?cloud=&offset=0&limit=20&moduleResourceUri=&activeOnly=1 no-body
  2016-02-02 11:32:19,510 INFO  - 200 (200 ms) GET /vms [no-authn-info] ?cloud=&offset=0&limit=20&moduleResourceUri=&activeOnly=1 no-body
  "
  [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)
          formatted-request (format-request request)
          _ (log/debug formatted-request)
          {:keys [status] :as response} (handler request)
          _ (log-response status (format-response formatted-request response start (System/currentTimeMillis)))]
      response)))
