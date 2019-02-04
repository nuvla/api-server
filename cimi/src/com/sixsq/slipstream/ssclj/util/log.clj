(ns com.sixsq.slipstream.ssclj.util.log
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.util.response :as r]
    [ring.util.response :as ring-resp]))

(defn log-and-throw
  "Logs the given message and returns an error response with the given status
   code and message."
  [status msg]
  (log/error status "-" msg)
  (throw (r/ex-response msg status)))

(defn log-error-and-throw-with-redirect
  "Logs the given message and returns an error response. The error response
   will contain the status code and message if the redirectURI is not provided.
   If the redirectURI is provided, then an error response with a redirect to
   the given URL will be provided. The error message is appended as the 'error'
   query parameter."
  [status msg redirectURI]
  (log/error status "-" msg)
  (if redirectURI
    (throw (r/ex-redirect msg nil redirectURI))
    (throw (r/ex-response msg status))))

(defn log-and-throw-400
  "Logs the given message as a warning and then throws an exception with a
   400 response."
  [msg]
  (let [response (-> {:status 400 :message msg}
                     r/json-response
                     (ring-resp/status 400))]
    (log/warn msg)
    (throw (ex-info msg response))))

(defn log-obj-info
  [o m]
  (log/info "--->>>" m)
  (let [s (new java.io.StringWriter)]
    (clojure.pprint/pprint o s)
    (log/info (str s)))
  (log/info m "<<---")
  o)
