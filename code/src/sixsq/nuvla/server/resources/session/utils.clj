(ns sixsq.nuvla.server.resources.session.utils
  (:require
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.session :as p]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.db.impl :as db]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.logging :as log]))


(defn session-edit
  "Session resources are not normally editable. Allow a session to be edited
   by bypassing all authorization checks."
  [{{:keys [id] :as body} :body :as request}]
  (try
    (-> (db/retrieve id {})
        (merge body)
        u/update-timestamps
        crud/validate
        (db/edit request))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn update-session
  "Updates an existing session resource. This isn't allowed through the API,
   but is needed when dealing with external authentication mechanisms."
  [session-id updated-session]
  (let [update-request {:params      {:resource-name p/resource-type}
                        :body        updated-session
                        :nuvla/authn auth/internal-identity}
        {status :status :as resp} (session-edit update-request)]
    (log/errorf "updating session %s\n%s" session-id (with-out-str (pprint update-request)))
    (if (= 200 status)
      resp
      (let [msg "cannot update session"]
        (throw (ex-info msg (r/map-response msg 500 session-id)))))))


(defn create-session
  "Creates a new session resource from the users credentials and the request
   header. The result contains the authentication method, the user's
   identifier, the client's IP address, and the virtual host being used. NOTE:
   The expiry is not included and MUST be added afterwards."
  [username user-id tpl-href headers authn-method & [redirect-url]]

  ;; supports headers that have either string or keyword keys
  ;; ring spec defines headers as lower-cased strings
  (let [server    (or (get headers "nuvla-ssl-server-name") (:nuvla-ssl-server-name headers))
        client-ip (or (get headers "x-real-ip") (:x-real-ip headers))]
    (crud/new-identifier
      (cond-> {:method   authn-method
               :user     user-id
               :template tpl-href}
              username (assoc :identifier username)
              server (assoc :server server)
              client-ip (assoc :client-ip client-ip)
              redirect-url (assoc :redirect-url redirect-url))
      p/resource-type)))


(defn create-callback
  [base-uri session-id action]
  (let [callback-request {:params      {:resource-name callback/resource-type}
                          :body        {:action          action
                                        :target-resource {:href session-id}}
                          :nuvla/authn auth/internal-identity}
        {{:keys [resource-id]} :body status :status} (crud/add callback-request)]
    (if (= 201 status)
      (if-let [callback-resource (crud/set-operations (crud/retrieve-by-id-as-admin resource-id) {})]
        (if-let [validate-op (u/get-op callback-resource "execute")]
          (str base-uri validate-op)
          (let [msg "callback does not have execute operation"]
            (throw (ex-info msg (r/map-response msg 500 resource-id)))))
        (let [msg "cannot retrieve  session callback"]
          (throw (ex-info msg (r/map-response msg 500 resource-id)))))
      (let [msg "cannot create  session callback"]
        (throw (ex-info msg (r/map-response msg 500 session-id)))))))
