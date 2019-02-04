;;
;; TODO: factorize common libraries to auth and ssclj project
;;
(ns com.sixsq.slipstream.ssclj.util.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [environ.core :as environ]
            [me.raynes.fs :as fs]))

(defn- find-resource
  [resource-path]
  (if-let [config-file (io/resource resource-path)]
    config-file
    (let [msg (str "Resource not found (must be in classpath): '" resource-path "'")]
      (log/error msg)
      (throw (IllegalArgumentException. msg)))))

(defn find-file
  [fpath]
  (if (fs/exists? (fs/expand-home fpath))
    fpath
    (find-resource fpath)))

(defn read-config
  [& [path]]
  (if-let [config-name (or path (environ/env :config-name))]
    (-> config-name
        find-file
        slurp
        edn/read-string)
    (throw (IllegalStateException. "No configuration found."))))

(def ^:private config (memoize read-config))

(defn- read-property-value
  [property-name & more]
  (let [v (get (config) property-name (first more))]
    (log/debug "property" property-name "=" v)
    v))

(def property-value (memoize read-property-value))
