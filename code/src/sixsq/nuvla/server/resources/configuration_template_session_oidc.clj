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
  {:service       service
   :name          "OIDC Authentication Configuration"
   :description   "OpenID Connect Authentication Configuration"
   :instance      "authn-name"
   :authorize-url "http://auth.example.com"
   :token-url     "http://token.example.com"
   :client-id     "server-assigned-client-id"
   :public-key    "ABCDEF..."})


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
