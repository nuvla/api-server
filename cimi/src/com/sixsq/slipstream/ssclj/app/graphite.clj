(ns com.sixsq.slipstream.ssclj.app.graphite
  (:require
    [clojure.tools.logging :as log]
    [environ.core :refer [env]]
    [metrics.reporters.graphite :as graphite])
  (:import
    [com.codahale.metrics MetricFilter]
    [java.util.concurrent TimeUnit]))


(def ^:dynamic *reporter* nil)


(defn set-reporter!
  [reporter]
  (alter-var-root #'*reporter* (constantly reporter)))


(defn create-reporter
  [host]
  (when-not *reporter*
    (set-reporter! (graphite/reporter {:host          host
                                       :prefix        "ssclj"
                                       :rate-unit     TimeUnit/SECONDS
                                       :duration-unit TimeUnit/MILLISECONDS
                                       :filter        MetricFilter/ALL})))
  *reporter*)


(defn start-graphite-reporter
  []
  (if-let [host (env :graphite-host)]
    (try
      (-> host
          (create-reporter)
          (graphite/start 10))
      (log/info "graphite metrics reporter started for" host)
      (catch Exception e
        (log/error "graphite metrics reporter start error:" (str e))))
    (log/info "graphite metrics reporter NOT started")))


(defn stop-graphite-reporter
  []
  (if-let [reporter *reporter*]
    (try
      (set-reporter! nil)
      (graphite/stop reporter)
      (log/info "graphite metrics reporter stopped")
      (catch Exception e
        (log/error "graphite metrics reporter stop error:" (str e))))
    (log/info "graphite metrics reporter NOT stopped")))
