(ns sixsq.nuvla.server.resources.session.utils
  (:require
    [ring.util.codec :as codec]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.session :as p]
    [sixsq.nuvla.server.util.response :as r]))

(defn validate-action-url-unencoded
  [base-uri session-id]
  (str base-uri session-id "/validate"))


(defn validate-action-url
  [base-uri session-id]
  (codec/url-encode (validate-action-url-unencoded base-uri session-id)))


(defn extract-session-id
  "Extracts the session identifier from a given URL."
  [uri]
  (second (re-matches #".*(session/[^/]+)/.*" uri)))


(defn extract-session-uuid
  "Extracts the session uuid from the session identifier."
  [session-id]
  (second (re-matches #"session/(.+)" session-id)))


(def internal-edit (std-crud/edit-fn p/resource-type))


(defn create-session
  "Creates a new session resource from the users credentials and the request
   header. The result contains the authentication method, the user's identifier,
   the client's IP address, and the virtual host being used. NOTE: The expiry
   is not included and MUST be added afterwards."
  [username tpl-href headers authn-method redirectURI]

  ;; supports headers that have either string or keyword keys
  ;; ring spec defines headers as lower-cased strings
  (let [server (or (get headers "nuvla-ssl-server-name") (:nuvla-ssl-server-name headers))
        client-ip (or (get headers "x-real-ip") (:x-real-ip headers))]
    (crud/new-identifier
      (cond-> {:method   authn-method
               :template {:href tpl-href}}
              username (assoc :username username)
              server (assoc :server server)
              client-ip (assoc :clientIP client-ip)
              redirectURI (assoc :redirectURI redirectURI))
      p/resource-type)))


(defn create-callback [base-uri session-id action]
  (let [callback-request {:params      {:resource-name callback/resource-type}
                          :body        {:action         action
                                        :targetResource {:href session-id}}
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
