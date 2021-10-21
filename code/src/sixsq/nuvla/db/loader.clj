(ns sixsq.nuvla.db.loader
  (:require
    [sixsq.nuvla.db.ephemeral-impl :as edb]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.util.namespace-utils :as dyn]))


(defn load-and-set-persistent-db-binding
  "Dynamically loads and sets the persistent database binding identified by
   the given namespace. This returns nil if the argument is nil."
  [db-binding-ns]
  (some-> db-binding-ns dyn/load-ns db/set-impl!))


(defn load-and-set-ephemeral-db-binding
  "Dynamically loads and sets the ephemeral database binding identified by
   the given namespace. This returns nil if the argument is nil."
  [db-binding-ns]
  (some-> db-binding-ns dyn/load-ns edb/set-impl!))
