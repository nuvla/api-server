(ns sixsq.nuvla.server.resources.configuration-template-session-mitreid
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-template :as p]
    [sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid :as cts-mitreid]))


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
  (p/register resource))
