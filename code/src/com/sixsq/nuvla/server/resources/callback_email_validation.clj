(ns com.sixsq.nuvla.server.resources.callback-email-validation
  "
Validates an email address. The process creating this callback sends the
action link to the email address to be verified. When the execute link is
visited, the email identifier is marked as validated.
"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.callback :as callback]
    [com.sixsq.nuvla.server.resources.callback.email-utils :as email-utils]
    [com.sixsq.nuvla.server.resources.callback.utils :as utils]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "email-validation")


(defmethod callback/execute action-name
  [{callback-id    :id
    {:keys [href]} :target-resource :as _callback-resource} _request]
  (try
    (let [{:keys [id validated] :as _email} (crud/retrieve-by-id-as-admin href)]
      (if-not validated
        (let [msg (str id " successfully validated")]
          (email-utils/validate-email! id)
          (log/info msg)
          (r/map-response msg 200 id))
        (do
          (utils/callback-failed! callback-id)
          (r/map-response (format "%s already validated" id) 400))))
    (catch Exception e
      (utils/callback-failed! callback-id)
      (or (ex-data e) (throw e)))))
