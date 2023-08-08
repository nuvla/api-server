(ns sixsq.nuvla.events.impl
  (:require [sixsq.nuvla.events.protocol :as p]))


(def ^:dynamic *impl*)


(defn set-impl!
  [impl]
  (alter-var-root #'*impl* (constantly impl)))


(defn add [request]
  (p/add *impl* request))


(defn retrieve [request]
  (p/retrieve *impl* request))


(defn retrieve-by-id [resource-id & [request]]
  (p/retrieve-by-id *impl* resource-id request))


(defn edit [request]
  (p/edit *impl* request))


(defn delete [request]
  (p/delete *impl* request))


(defn do-action [request]
  (p/do-action *impl* request))


(defn query [opts]
  (p/query *impl* opts))


(defn add-collection-event [request resource-type event]
  (p/add-collection-event *impl* request resource-type event))


(defn add-resource-event [request resource-id event]
  (p/add-resource-event *impl* request resource-id event))


(defn search [opts]
  (p/search *impl* opts))


(defn wrap-crud-add [add-fn]
  (p/wrap-crud-add *impl* add-fn))


(defn wrap-crud-edit [edit-fn]
  (p/wrap-crud-edit *impl* edit-fn))


(defn wrap-crud-delete [delete-fn]
  (p/wrap-crud-delete *impl* delete-fn))


(defn wrap-action [action-fn]
  (p/wrap-action *impl* action-fn))
