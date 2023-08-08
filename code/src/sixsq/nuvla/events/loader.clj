(ns sixsq.nuvla.events.loader
  (:require [sixsq.nuvla.events.impl :as events]
            [sixsq.nuvla.server.util.namespace-utils :as dyn]))


(defn load-and-set-event-manager
  "Dynamically loads and sets the event manager identified by
   the given namespace. This returns nil if the argument is nil."
  [db-binding-ns]
  (some-> db-binding-ns dyn/load-ns events/set-impl!))

