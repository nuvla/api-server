(ns com.sixsq.nuvla.server.middleware.exception-handler
  (:require
    [clj-stacktrace.repl :as st]
    [clojure.tools.logging :as log]
    [ring.util.response :as r]))

(defn treat-unexpected-exception
  [e]
  (let [msg      (str "Unexpected exception thrown: " (.getMessage e))
        body     {:status 500 :message msg}
        response (r/status (r/response body) 500)]
    (log/error (str msg "\n" (st/pst-str e)))
    response))

(defn wrap-exceptions [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (let [response (ex-data e)]
          (if (r/response? response)
            response
            (treat-unexpected-exception e)))))))
