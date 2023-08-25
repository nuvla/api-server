(ns sixsq.nuvla.server.resources.common.event-config
  (:require [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.utils :as u]))

;;
;; Dispatch functions
;;

(defn resource-type-dispatch [resource-type]
  resource-type)


(defn event-name-dispatch [{event-name :name :as _event} & rest]
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


;;
;; Event human readable description
;;

(defmulti event-description
          "Returns a human-readable description of the event"
          event-name-dispatch)


(defmethod event-description :default
  [{:keys [success authn-info category content] event-name :name :as _event}]
  (if success
    (let [user-name-or-id (or (some-> authn-info :user-id crud/retrieve-by-id-as-admin1 :name)
                              (:user-id authn-info))
          resource-id     (-> content :resource :href)
          resource-type   (u/id->resource-type resource-id)
          resource-name   (:name (crud/retrieve-by-id-as-admin1 resource-id))
          resource-name-or-id (or resource-name resource-id)]
      (case category
        ("add" "edit" "delete" "action")
        (str (or user-name-or-id "An anonymous user")
             (case category
               "add" (str " added " resource-type " " resource-name-or-id)
               "edit" (str " edited " resource-type " " resource-name-or-id)
               "delete" (str " deleted " resource-type " " resource-name-or-id)
               "action" (let [action (some->> event-name (re-matches #".*\.(.*)") second)]
                          (str " executed action " action " on " resource-type " " resource-name-or-id))
               nil)
             ".")
        ("state" "alarm" "email" "user")
        event-name                                          ;; FIXME: improve description in this case
        event-name))
    (str event-name " attempt failed.")))


