(ns sixsq.nuvla.events.protocol)

(defprotocol EventManager
  "This protocol defines the interface to the underlying event manager.
   It provides an implementation for the standard crud functions for the
   `event` resource type, and some wrappers for the crud interface to
   augment the standard crud functionality with recording of events."

  ;; standard crud operations, for events
  (add [this request])
  (retrieve [this request])
  (retrieve-by-id [this resource-id request])
  (edit [this request])
  (delete [this request])
  (do-action [this request])
  (query [this request])

  ;; utility functions
  (add-collection-event [this request resource-type event])
  (add-resource-event [this request resource-id event])
  (search [this opts])

  ;; crud wrappers to augment standard functionality with event recording
  (wrap-crud-add [this add-fn])
  (wrap-crud-edit [this edit-fn])
  (wrap-crud-delete [this delete-fn])
  (wrap-action [this action-fn]))
