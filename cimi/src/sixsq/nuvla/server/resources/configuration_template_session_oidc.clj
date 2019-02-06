(ns sixsq.nuvla.server.resources.configuration-template-session-oidc
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-template :as p]
    [sixsq.nuvla.server.resources.spec.configuration-template-session-oidc :as cts-oidc]))


(def ^:const service "session-oidc")


;;
;; resource
;;

(def ^:const resource
  {:service     service
   :name        "OIDC Authentication Configuration"
   :description "OpenID Connect Authentication Configuration"
   :instance    "authn-name"
   :authorizeURL "http://auth.example.com"
   :tokenURL    "http://token.example.com"
   :clientID    "server-assigned-client-id"
   :publicKey   "ABCDEF..."})



;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-oidc/schema))
(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Configuration template
;;

(defn initialize
  []
  (p/register resource))
