(ns sixsq.nuvla.server.resources.session-api-key
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template-api-key :as api-key-tpl]
    [sixsq.nuvla.server.resources.credential.key-utils :as key-utils]
    [sixsq.nuvla.server.resources.session :as p]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.resources.spec.session-template-api-key :as st-api-key]
    [sixsq.nuvla.util.response :as r]))


(def ^:const authn-method "api-key")


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::session/session))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::st-api-key/schema-create))
(defmethod p/create-validate-subtype authn-method
  [resource]
  (create-validate-fn resource))

;;
;; transform template into session resource
;;

(defn uuid->id
  "Add the resource type to the document UUID to recover the document id."
  [uuid]
  (if (re-matches #"^credential/.*$" uuid)
    uuid
    (str "credential/" uuid)))

(defn retrieve-credential-by-id
  "Retrieves a credential based on its identifier. Bypasses the authentication
   controls in the database CRUD layer. If the document doesn't exist or any
   error occurs, then nil is returned."
  [doc-id]
  (try
    (crud/retrieve-by-id-as-admin (uuid->id doc-id))
    (catch Exception _
      nil)))

(defn valid-api-key?
  "Checks that the API key document is of the correct type, hasn't expired,
   and that the digest matches the given secret."
  [{:keys [digest expiry type] :as api-key} secret]
  (and (= api-key-tpl/credential-type type)
       (u/not-expired? expiry)
       (key-utils/valid? secret digest)))

(defn create-claims [username roles headers session-id client-ip]
  (let [server (:slipstream-ssl-server-name headers)]
    (cond-> {:username username, :roles (str/join " " roles)}
            server (assoc :server server)
            session-id (assoc :session session-id)
            session-id (update :roles #(str % " " session-id))
            client-ip (assoc :clientIP client-ip))))

(defmethod p/tpl->session authn-method
  [{:keys [href redirectURI key secret] :as resource} {:keys [headers base-uri] :as request}]
  (let [{{:keys [identity roles]} :claims :as api-key} (retrieve-credential-by-id key)]
    (if (valid-api-key? api-key secret)
      (let [session (sutils/create-session {:username identity, :href href} headers authn-method)
            claims (create-claims identity roles headers (:id session) (:clientIP session))
            cookie (cookies/claims-cookie claims)
            expires (ts/rfc822->iso8601 (:expires cookie))
            claims-roles (:roles claims)
            session (cond-> (assoc session :expiry expires)
                            claims-roles (assoc :roles claims-roles))]
        (log/debug "api-key cookie token claims for" (u/document-id href) ":" claims)
        (let [cookies {(sutils/cookie-name (:id session)) cookie}]
          (if redirectURI
            [{:status 303, :headers {"Location" redirectURI}, :cookies cookies} session]
            [{:cookies cookies} session])))
      (if redirectURI
        (throw (r/ex-redirect (str "invalid API key/secret credentials for '" key "'") nil redirectURI))
        (throw (r/ex-unauthorized key))))))


;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize p/resource-type ::session/session))
