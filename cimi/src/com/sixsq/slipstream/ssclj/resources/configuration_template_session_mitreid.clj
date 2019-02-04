(ns com.sixsq.slipstream.ssclj.resources.configuration-template-session-mitreid
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.configuration-template :as p]
    [com.sixsq.slipstream.ssclj.resources.spec.configuration-template-session-mitreid :as cts-mitreid]))


(def ^:const service "session-mitreid")


;;
;; resource
;;

(def ^:const resource
  {:service        service
   :name           "MITREid Authentication Configuration"
   :description    "MITREid OpenID Connect Authentication Configuration"
   :instance       "authn-name"
   :authorizeURL   "http://auth.example.com"
   :tokenURL       "http://token.example.com"
   :userProfileURL "http://userinfo.example.com"
   :clientID       "server-assigned-client-id"
   :clientSecret   "aaabbbcccdddd"
   :publicKey      "ABCDEF..."})


;;
;; description
;;

(def ^:const desc
  (merge p/ConfigurationTemplateDescription
         {:clientID       {:displayName "Client ID"
                         :type        "string"
                         :description "client identifier assigned by the MITREid server"
                         :mandatory   true
                         :readOnly    false
                         :order       20}
          :clientSecret   {:displayName "Client Secret"
                         :type        "password"
                         :description "client secret assigned by the MITREid server"
                         :mandatory   true
                         :readOnly    false
                         :order       21}
          :publicKey      {:displayName "Public Key"
                         :type        "string"
                         :description "public key to verify signed tokens from the MITREid server"
                         :mandatory   true
                         :readOnly    false
                         :order       22}
          :authorizeURL   {:displayName "Authorize URL"
                         :type        "string"
                         :description "server's endpoint authentication URL for the MITREid protocol"
                         :mandatory   true
                         :readOnly    false
                         :order       23}
          :tokenURL       {:displayName "Token URL"
                         :type        "string"
                         :description "server's endpoint token URL for the MITREid protocol"
                         :mandatory   true
                         :readOnly    false
                         :order       24}
          :userProfileURL {:displayName "User information URL"
                         :type          "string"
                         :description   "server's endpoint user info URL for the MITREid protocol"
                         :mandatory     true
                         :readOnly      false
                         :order         25}}))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-mitreid/schema))
(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Configuration template
;;

(defn initialize
  []
  (p/register resource desc))
