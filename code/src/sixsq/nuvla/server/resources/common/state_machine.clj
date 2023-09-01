(ns sixsq.nuvla.server.resources.common.state-machine
  (:require [clojure.string :as str]
            [sixsq.nuvla.server.util.response :as r]
            [statecharts.core :as fsm]))

(defmulti state-machine :resource-type)

(defmethod state-machine :default
  [_resource]
  nil)

(defn -state->fsm-state
  [^String state]
  {:_state (-> state str/lower-case keyword)})

(defn -fsm-state->state
  [{:keys [_state]}]
  (-> _state name str/upper-case))

(defn transition
  [resource action]
  (if-let [fsm (state-machine resource)]
    (update resource :state
            #(-> fsm
                 (fsm/transition (-state->fsm-state %) {:type (keyword action)})
                 -fsm-state->state))
    resource))

(defn initialize
  [resource]
  (if-let [fsm (state-machine resource)]
    (assoc resource :state (-fsm-state->state (fsm/initialize fsm)))
    resource))

(defn can-do-action?
  [resource action]
  (try
    (transition resource action)
    true
    (catch Exception _
      false)))

(defn throw-can-not-do-action
  [{:keys [id state] :as resource} {{:keys [action]} :params :as _request}]
  (if (can-do-action? resource action)
    resource
    (throw (r/ex-response (format "%s action is not allowed in state [%s]"
                                  action state id) 409 id))))
