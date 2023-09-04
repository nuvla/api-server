(ns sixsq.nuvla.server.resources.common.state-machine
  (:require [sixsq.nuvla.server.util.response :as r]
            [tilakone.core :as tk]
            [tilakone.util :as tku]))

(defmulti state-machine :resource-type)

(defmethod state-machine :default
  [_resource]
  nil)

(defn force-state
  [fsm state]
  (assoc fsm ::tk/state state))

(defn get-state
  [fsm]
  (::tk/state fsm))

(defn fsm-resource
  [{:keys [state] :as resource}]
  (when-let [fsm (state-machine resource)]
    (force-state fsm state)))

(defn transition
  [resource action]
  (if-let [fsm (fsm-resource resource)]
    (assoc resource :state (get-state (tk/apply-signal fsm action)))
    resource))

(defn initialize
  [resource]
  (if-let [fsm (state-machine resource)]
    (assoc resource :state (get-state fsm))
    resource))

(defn can-do-action?
  [resource action]
  (if-let [fsm (fsm-resource resource)]
    (some? (tk/transfers-to fsm action))
    true))

(defn throw-action-not-allowed-in-state
  [id action state]
  (throw (r/ex-response (format "%s action is not allowed in state [%s]"
                                action state id) 409 id)))

(defn throw-can-not-do-action
  [{:keys [id state] :as resource} {{:keys [action]} :params :as _request}]
  (if (can-do-action? resource action)
    resource
    (throw-action-not-allowed-in-state id action state)))
