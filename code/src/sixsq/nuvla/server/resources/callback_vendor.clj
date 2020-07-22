(ns sixsq.nuvla.server.resources.callback-vendor
  "
Creates a new application vendor resource presumably after stripe connect
registration has succeeded.
"
  (:require
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.vendor :as vendor]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.resources.pricing.stripe :as stripe]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]))


(def ^:const action-name "vendor-creation")


(defmethod callback/execute action-name
  [{callback-id :id {:keys [redirect-url state]} :data :as callback-resource}
   {{req-state :state code :code active-claim :active-claim} :params :as request}]
  (config-nuvla/throw-stripe-not-configured)
  (try
    (if (= state req-state)
      (let [account-id      (stripe/get-stripe-user-id
                              (stripe/oauth-token {"grant_type" "authorization_code"
                                                   "code"       code}))
            add-vendor-resp (vendor/add-impl
                              {:params      {:resource-name vendor/resource-type}
                               :nuvla/authn auth/internal-identity
                               :body        {:account-id account-id
                                             :acl        {:owners   ["group/nuvla-admin"]
                                                          :view-acl [active-claim]}}})]
        (if (= 201 (:status add-vendor-resp))
          (if redirect-url
            {:status 303, :headers {"Location" redirect-url}}
            add-vendor-resp)
          (let [msg "cannot create  vendor"]
            (throw (ex-info msg (r/map-response msg 500))))))
      (let [msg "Incorrect state parameter!"]
        (throw (ex-info msg (r/map-response msg 400)))))
    (catch Exception e
      (utils/callback-failed! callback-id)
      (or (ex-data e) (throw e)))))
