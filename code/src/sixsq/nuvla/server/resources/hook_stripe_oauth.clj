(ns sixsq.nuvla.server.resources.hook-stripe-oauth
  "
Stripe oauth hook.
"
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const action "stripe-oauth")


(defn execute
  [{{req-state :state code :code} :params :as _request}]
  (try
    (let [[resource uuid] (str/split req-state #"/")
          action "execute"]

      (crud/do-action {:params      {:resource-name resource
                                     :uuid          uuid
                                     :action        action
                                     :code          code}
                       :nuvla/authn auth/internal-identity}))
    (catch Exception _
      (let [msg "Incorrect state parameter!"]
        (throw (ex-info msg (r/map-response msg 400)))))))
