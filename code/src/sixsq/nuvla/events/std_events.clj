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


(defn crud-operation-event-types
  ([resource-type op crud-subcategory]
   (crud-operation-event-types resource-type op crud-subcategory nil))
  ([resource-type op crud-subcategory extra-opts]
   {(operation-requested-event-type resource-type op) (merge {:category "command"} extra-opts)
    (operation-completed-event-type resource-type op) (merge {:category "crud" :subcategory crud-subcategory} extra-opts)
    (operation-failed-event-type resource-type op)    (merge {:category "crud" :subcategory crud-subcategory} extra-opts)}))


(defn crud-event-types
  [resource-type]
  (merge
    (crud-operation-event-types resource-type "create" "resource-create")
    (crud-operation-event-types resource-type "update" "resource-delete")
    (crud-operation-event-types resource-type "delete" "resource-delete")))


(defn action-event-types
  ([resource-type action]
   (action-event-types resource-type action nil))
  ([resource-type action extra-opts]
   {(operation-requested-event-type resource-type action) (merge {:category "command"} extra-opts)
    (operation-completed-event-type resource-type action) (merge {:category "action"} extra-opts)
    (operation-failed-event-type resource-type action)    (merge {:category "action"} extra-opts)}))


(defn- resource-type-actions
  "Returns all supported actions for a given resource type."
  [resource-type]
  (->> (methods crud/do-action)
       keys
       (filter #(and (vector? %) ;; exclude :default dispatch value
                     (= resource-type (first %))))
       (map second)))


(defn actions-event-types
  ([resource-type]
   (actions-event-types resource-type (resource-type-actions resource-type)))
  ([resource-type actions]
   (actions-event-types resource-type actions nil))
  ([resource-type actions extra-opts]
   (reduce
     (fn [m action]
       (merge m (action-event-types resource-type action extra-opts)))
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

