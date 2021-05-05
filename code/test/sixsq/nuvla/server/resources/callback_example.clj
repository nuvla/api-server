(ns sixsq.nuvla.server.resources.callback-example
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.util.log :as log-util]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "example")


(defmethod callback/execute action-name
  [{{:keys [ok?]} :data id :id :as _callback-resource} _request]
  (if ok?
    (do
      (utils/callback-succeeded! id)
      (log/info (format "executing action %s of %s succeeded" action-name id))
      (r/map-response "success" 200 id))
    (do
      (utils/callback-failed! id)
      (log-util/log-and-throw 400 (format "executing action %s of %s FAILED" action-name id)))))
