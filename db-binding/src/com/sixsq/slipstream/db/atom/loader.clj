(ns com.sixsq.slipstream.db.atom.loader
  (:refer-clojure :exclude [load])
  (:require
    [com.sixsq.slipstream.db.atom.binding :as atom]))


(defn load
  "Creates an atom-based database binding. All data is stored in memory and is
   ephemeral. This implementation takes no parameters from the environment."
  []
  (-> {} atom atom/->AtomBinding))
