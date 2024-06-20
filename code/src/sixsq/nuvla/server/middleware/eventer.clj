(ns sixsq.nuvla.server.middleware.eventer
  (:require [clojure.tools.logging :as log]
            [sixsq.nuvla.server.resources.common.event-config :as config]
            [sixsq.nuvla.server.resources.common.event-context :as ec]
            [sixsq.nuvla.server.resources.event.utils :as eu]))

(defn wrap-eventer
  "Creates an event after handler execution, if eventing is enabled for the resource type."
  [handler]
  (fn [request]
    (ec/with-context
      (let [response      (handler request)
            resource-type (-> (ec/get-context) :params :resource-name)]
        (log/error "HERE (config/events-enabled? resource-type):" resource-type (config/events-enabled? resource-type))
        (when (config/events-enabled? resource-type)
          (let [event (eu/build-event (ec/get-context) request response)]
            (log/error "HERE2 event:" event)
            (log/error "HERE3 log-event?:" (config/log-event? event response))
            (when (config/log-event? event response)
              (log/error "(eu/add-event event):" (eu/add-event event)))))
        response))))
