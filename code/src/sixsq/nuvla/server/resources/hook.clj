(ns sixsq.nuvla.server.resources.hook
  "
The `hook` resource is a non standard cimi resource that provides an access
for events driven workflows.
"
  (:require
    [clojure.string :as str]
    [compojure.core :refer [ANY defroutes]]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.response :as r]))

;;
;; utilities
;;

(def ^:const resource-type (u/ns->type *ns*))


(def resource-acl {:owners   ["group/nuvla-admin"]
                   :view-acl ["group/nuvla-anon"]})

(def stripe-oauth "stripe-oauth")

(defn stripe-oauth-hook
  [{{req-state :state code :code} :params :as request}]
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


(defroutes routes
           (ANY (str p/service-context resource-type "/" stripe-oauth) request
             (stripe-oauth-hook request)))
