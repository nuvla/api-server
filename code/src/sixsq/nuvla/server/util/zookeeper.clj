(ns sixsq.nuvla.server.util.zookeeper
  (:require
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [zookeeper :as zk])
  (:import (org.apache.zookeeper KeeperException$SessionExpiredException)))

(def ^:dynamic *client* nil)

(defn set-client!
  [client]
  (alter-var-root #'*client* (constantly client)))

(defn create-client
  "Creates a client connecting to an instance of Zookeeper
  Parameters (host and port) are taken from environment variables."
  []
  (let [zk-endpoints (or (env/env :zk-endpoints) "localhost:2181")
        timeout-msec (Integer/parseInt (env/env :zk-connect-timeout-msec "15000"))]
  (log/info "creating zookeeper client:" zk-endpoints " timeout: " timeout-msec)
  (zk/connect zk-endpoints :timeout-msec timeout-msec)))

(defn close-client! []
  (when *client*
    (zk/close *client*)
    (set-client! nil)))

(defn string-to-byte [value]
  (.getBytes (str value) "UTF-8"))

(defmacro retry-zk-client [zk-func path & options]
  `(try
     (when (or (nil? *client*)
               (= (zk/state *client*) :CLOSED))
       (log/warn "zookeeper recreate client!")
       (set-client! (create-client)))
     (~zk-func *client* ~path ~@options)
     (catch KeeperException$SessionExpiredException _e#
       (log/warn "zookeeper session expired exception occurred!")
       (close-client!)
       (set-client! (create-client))
       (~zk-func *client* ~path ~options))))

(defmacro create-all [path & options]
  `(retry-zk-client zk/create-all ~path ~@options))

(defmacro create [path & options]
  `(retry-zk-client zk/create ~path ~@options))

(defmacro exists [path & options]
  `(retry-zk-client zk/exists ~path ~@options))
