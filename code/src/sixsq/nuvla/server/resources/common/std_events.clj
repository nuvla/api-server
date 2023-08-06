(ns sixsq.nuvla.server.resources.common.std-events
  (:require [sixsq.nuvla.server.resources.common.crud :as crud]))

(defn operation-requested-event-type [resource-type operation]
  (str resource-type "." operation ".requested"))

(defn operation-completed-event-type [resource-type operation]
  (str resource-type "." operation ".completed"))

(defn operation-failed-event-type [resource-type operation]
  (str resource-type "." operation ".failed"))


(defn state-changed-event-type [resource-type]
  (str resource-type ".state.changed"))


(defn crud-event-types [resource-type]
  {(operation-requested-event-type resource-type "create") {:category "command"}
   (operation-completed-event-type resource-type "create") {:category "crud" :sub-category "resource-create"}
   (operation-failed-event-type resource-type "create")    {:category "crud" :sub-category "resource-create"}

   (operation-requested-event-type resource-type "update") {:category "command"}
   (operation-completed-event-type resource-type "update") {:category "crud" :sub-category "resource-update"}
   (operation-failed-event-type resource-type "update")    {:category "crud" :sub-category "resource-update"}

   (operation-requested-event-type resource-type "delete") {:category "command"}
   (operation-completed-event-type resource-type "delete") {:category "crud" :sub-category "resource-delete"}
   (operation-failed-event-type resource-type "delete")    {:category "crud" :sub-category "resource-delete"}})


(defn actions-event-types [resource-type]
  (let [actions (->> (methods crud/do-action)
                     keys
                     (filter #(and (vector? %) (= resource-type (first %))))
                     (map second))]
    (reduce
      (fn [m action]
        (merge m
               {(operation-requested-event-type resource-type action) {:category "command"}
                (operation-completed-event-type resource-type action) {:category "action"}
                (operation-failed-event-type resource-type action)    {:category "action"}}))
      {}
      actions)))


(defn state-event-types [resource-type]
  {(state-changed-event-type resource-type) {:category "state"}})

