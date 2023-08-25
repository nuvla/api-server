(ns sixsq.nuvla.server.resources.common.event-config)

;;
;; Dispatch functions
;;

(defn resource-type-dispatch [resource-type]
  resource-type)


(defn event-name-dispatch [{event-name :name :as _event} _response]
  event-name)


;;
;; Enabled/disabled events
;;

(defmulti events-enabled?
          "Returns true if events should be logged for the given resource-type, false otherwise."
          resource-type-dispatch)


(defmethod events-enabled? :default
  [_resource-type]
  false)


;;
;; Whitelist and blacklist event types per resource type
;;

(defmulti log-event?
          "Returns true if the event should be logged, false otherwise."
          event-name-dispatch)


(defmethod log-event? :default
  [{event-name :name :as _event} {:keys [status] :as _response}]
  (and (not= 405 status)
       (some? event-name)))
