(ns sixsq.nuvla.events.config
  (:require [sixsq.nuvla.events.std-events :as std-events]
            [sixsq.nuvla.server.util.log :as logu]))

(defn resource-type-dispatch [resource-type]
  resource-type)


(defmulti events-enabled
          "Returns true if events should be logged for the given resource-type, false otherwise."
          resource-type-dispatch)


(defn events-enabled-impl [_] true)

(defmethod events-enabled :default
  [resource-type]
  (events-enabled-impl resource-type))


(defmulti supported-event-types
          "Returns a map where the keys are the event types supported by the resource type and
           the values are the configuration of the event types."
          resource-type-dispatch)


(defn supported-event-types-impl
  [resource-type]
  (merge
    (std-events/crud-event-types resource-type)
    (std-events/actions-event-types resource-type)))


(defmethod supported-event-types :default
  [resource-type]
  (supported-event-types-impl resource-type))


(defn get-event-config
  [resource-type event-type]
  (-> resource-type
      (supported-event-types)
      (get event-type)))


(defn event-supported?
  [{:keys [event-type resource]}]
  (some? (get-event-config (:resource-type resource) event-type)))

