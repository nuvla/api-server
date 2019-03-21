(ns sixsq.nuvla.server.resources.infrastructure-service.utils
  (:require
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.util.response :as r]))


(defn can-delete?
  [{:keys [state] :as resource}]
  (#{"CREATED" "STOPPED"} state))


(defn verify-can-delete
  [{:keys [id state] :as resource}]
  (if (can-delete? resource)
    resource
    (throw (r/ex-response (str "invalid state (" state ") for delete on " id) 412 id))))


(defn remove-delete
  [operations]
  (vec (remove #(= (:delete c/action-uri) (:rel %)) operations)))
