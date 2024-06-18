(ns sixsq.nuvla.db.es.log
  (:require [clojure.tools.logging :as log]
            [sixsq.nuvla.server.util.response :as r]))

(defn throw-bad-request-ex
  [msg]
  (throw (r/ex-bad-request msg)))

(defn throw-conflict-ex
  [id]
  (throw (r/ex-conflict id)))

(defn log-and-throw-bad-request-ex
  ([ex]
   (log-and-throw-bad-request-ex "bad request" ex))
  ([msg ex]
   (let [{:keys [status body]} (ex-data ex)
         error (:error body)]
     (log/error msg {:status status} (or error ex))
     (throw (r/ex-bad-request msg)))))

(defn log-and-throw-unexpected-es-status
  ([status expected-status-set]
   (log-and-throw-unexpected-es-status "unexpected status code" status expected-status-set))
  ([msg status expected-status-set]
   (log/error (str "unexpected status " status ". One of " expected-status-set " was expected.") msg)
   (throw (r/ex-response msg 500))))

(defn log-and-throw-unexpected-es-ex
  ([ex]
   (log-and-throw-unexpected-es-ex "unexpected error" ex))
  ([msg ex]
   (log-and-throw-unexpected-es-ex msg "unexpected error" ex))
  ([internal-msg external-msg ex]
   (let [{:keys [status body]} (ex-data ex)
         error (:error body)]
     (log/error internal-msg {:status status} (or error ex))
     (throw (r/ex-response external-msg 500)))))

