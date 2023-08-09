(ns sixsq.nuvla.events.std-events
  (:require [sixsq.nuvla.events.config :as config]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.events.spec.event-details :as event-details-spec]))

(defn operation-requested-event-type [resource-type operation]
  (str resource-type "." operation))


(defn operation-completed-event-type [resource-type operation]
  (str resource-type "." operation ".completed"))


(defn operation-failed-event-type [resource-type operation]
  (str resource-type "." operation ".failed"))


(defn state-changed-event-type [resource-type]
  (str resource-type ".state.changed"))


(defn crud-event-types [resource-type]
  {(operation-requested-event-type resource-type "create") {:category "command"}
   (operation-completed-event-type resource-type "create") {:category "crud" :subcategory "resource-create"}
   (operation-failed-event-type resource-type "create")    {:category "crud" :subcategory "resource-create"}

   (operation-requested-event-type resource-type "update") {:category "command"}
   (operation-completed-event-type resource-type "update") {:category "crud" :subcategory "resource-update"}
   (operation-failed-event-type resource-type "update")    {:category "crud" :subcategory "resource-update"}

   (operation-requested-event-type resource-type "delete") {:category "command"}
   (operation-completed-event-type resource-type "delete") {:category "crud" :subcategory "resource-delete"}
   (operation-failed-event-type resource-type "delete")    {:category "crud" :subcategory "resource-delete"}})


(defn action-event-types [resource-type action]
  {(operation-requested-event-type resource-type action) {:category "command"}
   (operation-completed-event-type resource-type action) {:category "action"}
   (operation-failed-event-type resource-type action)    {:category "action"}})


(defn- resource-type-actions
  "Returns all supported actions for a given resource type."
  [resource-type]
  (->> (methods crud/do-action)
       keys
       (filter #(and (vector? %) ;; exclude :default dispatch value
                     (= resource-type (first %))))
       (map second)))


(defn actions-event-types [resource-type]
  (let [actions (resource-type-actions resource-type)]
    (reduce
      (fn [m action]
        (merge m (action-event-types resource-type action)))
      {}
      actions)))


(defn state-event-types [resource-type]
  {(state-changed-event-type resource-type) {:category "state"}})

;;
;; Default implementation of config multi-fns
;;


(defn events-enabled?-impl
  [_resource-type]
  true)

(defmethod config/events-enabled? :default
  [resource-type]
  (events-enabled?-impl resource-type))


(defn supported-event-types-impl
  [resource-type]
  (merge
    (crud-event-types resource-type)
    (actions-event-types resource-type)))


(defmethod config/supported-event-types :default
  [resource-type]
  (supported-event-types-impl resource-type))


(defmethod config/category-default-details-spec "state"
  [_category]
  ::event-details-spec/state-event-details-schema)

