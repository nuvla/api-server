(ns sixsq.nuvla.server.resources.infrastructure-service.utils
  (:require
    [sixsq.nuvla.server.util.response :as r]))


(defn verify-can-delete
  [{:keys [id state] :as resource}]
  (if (#{"CREATED" "STOPPED"} state)
    resource
    (throw (r/ex-response (str "invalid state (" state ") for delete on " id) 412 id ))))
