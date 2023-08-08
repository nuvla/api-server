(ns sixsq.nuvla.events.db.loader
  (:refer-clojure :exclude [load])
  (:require [sixsq.nuvla.events.db.db-event-manager :as db-event-manager]))

(defn load
  "Creates an event handler backed by the current db implementation."
  []
  (db-event-manager/->DbEventManager))

