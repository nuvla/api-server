(ns sixsq.nuvla.server.resources.common.state-machine
  (:require [sixsq.nuvla.auth.acl-resource :as a]
            [sixsq.nuvla.server.util.response :as r]
            [tilakone.core :as tk]))

(defmulti state-machine :resource-type)

(defmethod state-machine :default
  [_resource]
  nil)

(defn get-state
  [fsm]
  (::tk/state fsm))

(defn fsm-resource-request
  [{:keys [state] :as resource} request]
  (when-let [fsm (state-machine resource)]
    (assoc fsm ::tk/state state
               :resource resource
               :request request)))

(defn transition
  [resource {{:keys [action]} :params :as request}]
  (if-let [fsm (fsm-resource-request resource request)]
    (assoc resource :state (get-state (tk/apply-signal fsm action)))
    resource))

(defn initialize
  [resource]
  (if-let [fsm (state-machine resource)]
    (assoc resource :state (get-state fsm))
    resource))

(defn can-do-action?
  [action resource request]
  (if-let [fsm (fsm-resource-request resource request)]
    (some? (tk/transfers-to fsm action))
    true))

(defn throw-action-not-allowed-in-state
  [id action state]
  (throw (r/ex-response (format "%s action is not allowed in state [%s]"
                                action state id) 409 id)))

(defn throw-can-not-do-action
  [{:keys [id state] :as resource} {{:keys [action]} :params :as request}]
  (if (can-do-action? action resource request)
    resource
    (throw-action-not-allowed-in-state id action state)))


(def guard-is-admin? :is-admin?)
(def guard-can-manage? :can-manage?)
(def guard-can-edit? :can-edit?)
(def guard-can-delete? :can-delete?)

(defn guard?
  [{{:keys [resource request]} ::tk/process
    guard                      ::tk/guard}]
  (condp = guard
    guard-can-manage? (a/can-manage? resource request)
    guard-is-admin? (a/is-admin-request? request)
    guard-can-edit? (a/can-edit? resource request)
    guard-can-delete? (a/can-delete? resource request)
    false))
