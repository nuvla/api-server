(ns sixsq.nuvla.server.resources.hook
  "
The `hook` resource is a non standard cimi resource that provides an access
for events driven workflows.
"
  (:require
    [clojure.string :as str]
    [compojure.core :refer [ANY defroutes]]
    [ring.util.codec :as codec]
    [sixsq.nuvla.auth.password :as auth-password]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.callback-user-email-validation :as user-email-validation]
    [sixsq.nuvla.server.resources.callback-user-password-set :as callback-pass-set]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
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

(def reset-password "reset-password")

(defn reset-password-hook
  [{{:keys [username redirect-url]} :body base-uri :base-uri :as request}]
  (let [{user-id  :id
         email-id :email
         state    :state :as user} (auth-password/extract-user username)

        {email-address :address :as email} (when email-id (crud/retrieve-by-id-as-admin email-id))]

    (when-not email-address
      (throw (r/ex-response (str "invalid username '" username "'") 400)))

    (when (= state "SUSPENDED")
      (throw (r/ex-response (format "%s is in 'SUSPENDED' state" user-id) 400)))

    (let [callback-url        (callback-pass-set/create-callback
                                base-uri user-id :expires (u/ttl->timestamp 86400)) ;; 1 day

          ui-set-password-url (str redirect-url "?callback=" (codec/url-encode callback-url)
                                   "&type=" (codec/url-encode "reset-password")
                                   "&username=" (codec/url-encode username))]


      (email-utils/send-password-set-email ui-set-password-url email-address)

      (r/map-response "An email with instructions has been sent to your email address." 200))))


(defroutes routes
           (ANY (str p/service-context resource-type "/" stripe-oauth) request
             (stripe-oauth-hook request))
           (ANY (str p/service-context resource-type "/" reset-password) request
             (reset-password-hook request)))
