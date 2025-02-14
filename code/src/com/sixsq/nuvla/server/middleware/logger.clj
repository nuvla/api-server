(ns com.sixsq.nuvla.server.middleware.logger
  (:require
    [clojure.string :as str]
    [taoensso.telemere :as telemere]
    [com.sixsq.nuvla.server.middleware.authn-info :as auth-info]))

(defn- request-querystring
  [request]
  (-> request
      :query-string
      (or "")
      (str/replace #"&?password=([^&]*)" "")))

(defn- request-authn-info
  [request]
  (or (auth-info/extract-header-authn-info request)
      (auth-info/extract-cookie-authn-info request)))

(defn response-status->log-level
  [status]
  (cond
    (<= 100 status 399) :info
    (<= 400 status 499) :warn
    (<= 500 status 599) :error
    :else :error))

(defn request-log-data
  [{:keys [uri request-method content-type] :as request}]
  (let [authn-info   (request-authn-info request)
        query-string (request-querystring request)]
    (cond-> {:method       (-> request-method name str/upper-case)
             :uri          uri
             :content-type content-type}
            authn-info (assoc :authn-info authn-info)
            (not (str/blank? query-string)) (assoc :query-string query-string))))

(defn response-log-data
  [request-data start end status]
  (assoc request-data
    :status status
    :duration-ms (- end start)))

(defn wrap-logger
  "Logs both request and response"
  [handler]
  (fn [request]
    (let [start         (System/currentTimeMillis)
          request-data  (request-log-data request)
          _             (telemere/log! {:id      "request"
                                        :level   :debug
                                        :data request-data})
          {:keys [status] :as response} (handler request)
          response-data (response-log-data request-data start (System/currentTimeMillis) status)]
      (telemere/log! {:id    "response"
                      :level (response-status->log-level status)
                      :data  response-data})
      response)))
