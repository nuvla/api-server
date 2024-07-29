(ns com.sixsq.nuvla.server.resources.callback-join-group
  "
Allow invited user to join a group. The process creating this callback sends the
action link to the email address to be verified. When the execute link is
visited, the email identifier is marked as validated.
"
  (:require
    [com.sixsq.nuvla.auth.password :as auth-password]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.server.resources.callback :as callback]
    [com.sixsq.nuvla.server.resources.callback.utils :as utils]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.credential-hashed-password :as hashed-password]
    [com.sixsq.nuvla.server.resources.user-template :as p]
    [com.sixsq.nuvla.server.resources.user-template-minimum :as user-minimum]
    [com.sixsq.nuvla.server.resources.user.utils :as user-utils]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const action-name "join-group")


(def create-callback (partial callback/create action-name))


(defn create-user
  [email new-password]
  (when-not (hashed-password/acceptable-password? new-password)
    (throw (r/ex-response hashed-password/acceptable-password-msg 400)))
  (std-crud/add-if-absent
    (str user-utils/resource-url " '" email "'") user-utils/resource-url
    {:template
     {:href     (str p/resource-type "/" user-minimum/registration-method)
      :email    email
      :password new-password}})
  (auth-password/identifier->user-id email))


(defn add-user-to-group
  [group-id user-id]
  (let [{:keys [users]} (crud/retrieve-by-id-as-admin group-id)
        {:keys [status body]} (crud/edit
                                {:params      {:uuid          (u/id->uuid group-id)
                                               :resource-name (u/id->resource-type group-id)}
                                 :nuvla/authn auth/internal-identity
                                 :body        {:users (-> users (conj user-id) distinct vec)}})]
    (if (= 200 status)
      body
      (let [msg (str (format "adding %s to %s failed:" user-id group-id)
                     status (:message body))]
        (throw (r/ex-bad-request msg))))))


(defmethod callback/execute action-name
  [{callback-id                      :id
    {existing-user-id :user-id
     email            :email
     redirect-url     :redirect-url} :data
    {group-id :href}                 :target-resource :as _callback-resource}
   {{:keys [new-password]} :body :as _request}]
  (try
    (let [user-id (or existing-user-id
                      (auth-password/identifier->user-id email) ;; in case user created an account in meantime
                      (create-user email new-password))
          msg     (format "'%s' successfully joined '%s'" user-id group-id)]
      (add-user-to-group group-id user-id)
      (utils/callback-succeeded! callback-id)
      (if (and existing-user-id redirect-url)
        (r/map-response msg 303 callback-id redirect-url)
        (r/map-response msg 200)))
    (catch Exception e
      (utils/callback-failed! callback-id)
      (or (ex-data e) (throw e)))))
