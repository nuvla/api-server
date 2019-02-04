(ns com.sixsq.slipstream.db.loader
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.db.ephemeral-impl :as edb]
    [com.sixsq.slipstream.db.impl :as db]))


(defn load-db-binding
  "Dynamically requires the binding identified by the given namespace and then
   executes the 'load' function in that namespace. Will log and then rethrow
   exceptions."
  [db-binding-ns]
  (try
    (-> db-binding-ns symbol require)
    (catch Exception e
      (log/errorf "cannot require namespace %s: %s" db-binding-ns (.getMessage e))
      (throw e)))
  (try
    (let [load (-> db-binding-ns symbol find-ns (ns-resolve 'load))]
      (let [binding-impl (load)]
        (log/infof "created binding implementation from %s" db-binding-ns)
        binding-impl))
    (catch Exception e
      (log/errorf "error executing load function from %s: %s" db-binding-ns (.getMessage e))
      (throw e))))


(defn load-and-set-persistent-db-binding
  "Dynamically loads and sets the persistent database binding identified by
   the given namespace. This returns nil if the argument is nil."
  [db-binding-ns]
  (some-> db-binding-ns load-db-binding db/set-impl!))


(defn load-and-set-ephemeral-db-binding
  "Dynamically loads and sets the ephemeral database binding identified by
   the given namespace. This returns nil if the argument is nil."
  [db-binding-ns]
  (some-> db-binding-ns load-db-binding edb/set-impl!))
