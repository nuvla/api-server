(ns sixsq.nuvla.events.noevents.loader
  (:refer-clojure :exclude [load])
  (:require [sixsq.nuvla.events.noevents.noevents-event-manager :as noevents-event-manager]))

(defn load
  "Creates a null event handler which does not log any event."
  []
  (noevents-event-manager/->NoEventsEventManager))

