(ns sixsq.nuvla.events.noevents.noevents-event-manager
  (:require [sixsq.nuvla.events.protocol :refer [EventManager]]))

(deftype NoEventsEventManager []
  EventManager
  (add-collection-event [_ _request _resource-type _event])
  (add-resource-event [_ _request _resource-id _event])
  (wrap-crud-add [_ add-fn]
    add-fn)
  (wrap-crud-edit [_ edit-fn]
    edit-fn)
  (wrap-crud-delete [_ delete-fn]
    delete-fn)
  (wrap-action [_ action-fn]
    action-fn)
  (search [_ _opts]
    []))
