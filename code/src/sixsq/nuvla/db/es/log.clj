(ns sixsq.nuvla.db.es.log
  (:require [clojure.tools.logging :as log]
            [sixsq.nuvla.server.util.response :as r]))

(defn throw-bad-request-ex
  [msg]
  (throw (r/ex-bad-request msg)))

(defn throw-conflict-ex
  [id]
  (throw (r/ex-conflict id)))

(defn log-and-throw-unexpected-es-status
  ([status expected-status-set]
   (log-and-throw-unexpected-es-status "unexpected status code" status expected-status-set))
  ([msg status expected-status-set]
   (log/error (str "unexpected status " status ". One of " expected-status-set " was expected.") msg)
   (throw (r/ex-response msg 500))))

(defn log-and-throw-unexpected-es-ex
  ([ex]
   (log-and-throw-unexpected-es-ex "unexpected exception" ex))
  ([msg ex]
   (let [{:keys [status body]} (ex-data ex)
         error (:error body)]
     (log/error msg {:status status} (or error ex))
     (throw (r/ex-response msg 500)))))
