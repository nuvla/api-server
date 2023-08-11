(ns sixsq.nuvla.events.noevents.noevents-event-manager
  (:require [sixsq.nuvla.events.protocol :refer [EventManager]]))

(deftype NoEventsEventManager []
  EventManager

  (add [_this request])
  (retrieve [_this request])
  (retrieve-by-id [_this resource-id request])
  (edit [_this request])
  (delete [_this request])
  (do-action [_this request])
  (query [_this request])

  (add-collection-event [_this _request _resource-type _event])
  (add-resource-event [_this _request _resource-id _event])
  (search [_this _opts]
    [])

  (wrap-crud-add [_this add-fn]
    add-fn)
  (wrap-crud-edit [_this edit-fn]
    edit-fn)
  (wrap-crud-delete [_this delete-fn]
    delete-fn)
  (wrap-action [_this action-fn]
    action-fn))
