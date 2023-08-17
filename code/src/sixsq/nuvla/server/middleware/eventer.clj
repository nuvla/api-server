(ns sixsq.nuvla.server.middleware.eventer
  (:require [sixsq.nuvla.server.resources.common.event-config :as config]
            [sixsq.nuvla.server.resources.common.event-context :as ec]
            [sixsq.nuvla.server.resources.event.utils :as eu]))

(defn wrap-eventer
  "Creates an event after handler execution, if eventing is enabled for the resource type."
  [handler]
  (fn [request]
    (ec/with-context
      (let [response      (handler request)
            resource-type (-> (ec/get-context) :params :resource-name)]
        (when (config/events-enabled? resource-type)
          (let [event (eu/build-event (ec/get-context) request response)]
            (when (config/log-event? event response)
              (eu/add-event event))))
        response))))
