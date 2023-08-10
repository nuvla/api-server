(ns sixsq.nuvla.events.db.db-event-manager
  (:require [sixsq.nuvla.auth.acl-resource :as a]
            [sixsq.nuvla.db.impl :as db]
            [sixsq.nuvla.events.config :as config]
            [sixsq.nuvla.events.protocol :refer [EventManager]]
            [sixsq.nuvla.events.util.event-manager-utils :as utils]
            [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
            [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
            [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
            [sixsq.nuvla.server.resources.event :as event]
            [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
            [sixsq.nuvla.server.util.response :as r]))


;;
;; CRUD
;;


(def add-impl (std-crud/add-fn event/resource-type event/collection-acl event/resource-type))


(defn- -add
  [_this {:keys [body] :as request}]
  (when (config/events-enabled? (-> body :resource :resource-type))
    (let [resp (-> request
                   utils/enrich-event
                   utils/validate-parent
                   utils/set-acl
                   add-impl)]
      (ka-crud/publish-on-add event/resource-type resp)
      resp)))


(def retrieve-impl (std-crud/retrieve-fn event/resource-type))


(defn -retrieve [_this request]
  (retrieve-impl request))


(defn -retrieve-by-id [_this resource-id request]
  (some-> resource-id
          (db/retrieve (or request {}))
          (a/throw-cannot-view request)))


(defn -edit [_this request]
  ;; events are immutable
  (throw (r/ex-bad-method request)))


(defn -delete [_this request]
  ;; events are immutable
  (throw (r/ex-bad-method request)))


(defn -do-action [_this request]
  ;; no actions on events
  (throw (r/ex-bad-method request)))


(def query-impl (std-crud/query-fn event/resource-type event/collection-acl event/collection-type))


(defn -query [_this {{:keys [orderby]} :cimi-params :as request}]
  (query-impl
    (assoc-in request [:cimi-params :orderby] (if (seq orderby) orderby [["timestamp" :desc]]))))


(deftype DbEventManager []
  EventManager

  (add [this request]
    (-add this request))
  (retrieve [this request]
    (-retrieve this request))
  (retrieve-by-id [this resource-id request]
    (-retrieve-by-id this resource-id request))
  (edit [this request]
    (-edit this request))
  (delete [this request]
    (-delete this request))
  (do-action [this request]
    (-do-action this request))
  (query [this request]
    (-query this request))

  (add-collection-event [this request resource-type event]
    (utils/-add-collection-event this request resource-type event))
  (add-resource-event [this request resource-id event]
    (utils/-add-resource-event this request resource-id event))
  (search [this opts]
    (utils/-search this opts))

  (wrap-crud-add [this add-fn]
    (utils/-wrap-crud-add this add-fn))
  (wrap-crud-edit [this edit-fn]
    (utils/-wrap-crud-edit this edit-fn))
  (wrap-crud-delete [this delete-fn]
    (utils/-wrap-crud-delete this delete-fn))
  (wrap-action [this action-fn]
    (utils/-wrap-action this action-fn)))
