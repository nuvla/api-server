(ns com.sixsq.slipstream.ssclj.util.zookeeper
  (:require
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [zookeeper :as zk])
  (:import (org.apache.zookeeper KeeperException$SessionExpiredException)))

(def ^:dynamic *client*)

(defn set-client!
  [client]
  (alter-var-root #'*client* (constantly client)))

(defn create-client
  "Creates a client connecting to an instance of Zookeeper
  Parameters (host and port) are taken from environment variables."
  []
  (let [zk-endpoints (or (env/env :zk-endpoints) "localhost:2181")]
    (log/info "creating zookeeper client:" zk-endpoints)
    (zk/connect zk-endpoints)))

(defn close-client! []
  (when *client*
    (zk/close *client*)
    (set-client! nil)))

(defn string-to-byte [value]
  (.getBytes (str value) "UTF-8"))

(defmacro retry-zk-client [zk-func path & options]
  `(try
     (when (or (nil? *client*)
               (instance? clojure.lang.Var$Unbound *client*))
       (set-client! (create-client)))
     (~zk-func *client* ~path ~@options)
     (catch KeeperException$SessionExpiredException e#
       (log/warn "zookeeper session expired exception occurred!")
       (close-client!)
       (set-client! (create-client))
       (~zk-func *client* ~path ~options))))

(defmacro create-all [path & options]
  `(retry-zk-client zk/create-all ~path ~@options))

(defmacro create [path & options]
  `(retry-zk-client zk/create ~path ~@options))

(defmacro get-znode [path & options]
  `(let [result# (retry-zk-client zk/data ~path ~@options)
         data# (:data result#)
         value# (when (-> data# nil? not) (String. data#))]
     (assoc result# :data value#)))

(defmacro get-data [path & options]
  `(-> (get-znode ~path ~@options)
       :data))

(defmacro get-stat [path & options]
  `(-> (retry-zk-client get-znode ~path ~@options)
       :stat))

(defn get-version
  [path]
  (-> (get-znode path) :stat :version))

(defmacro set-data [path value & options]
  `(let [version# (get-version ~path)
         data# (string-to-byte ~value)]
     (retry-zk-client zk/set-data ~path data# version# ~@options)))

(defmacro exists [path & options]
  `(retry-zk-client zk/exists ~path ~@options))

(defmacro children [path & options]
  `(retry-zk-client zk/children ~path ~@options))

(defmacro delete-all [path & options]
  `(retry-zk-client zk/delete-all ~path ~@options))

(defmacro delete [path & options]
  `(retry-zk-client zk/delete ~path ~@options))
