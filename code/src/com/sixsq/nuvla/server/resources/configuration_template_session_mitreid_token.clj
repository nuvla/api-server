(ns com.sixsq.nuvla.server.resources.configuration-template-session-mitreid-token
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-template :as p]
    [com.sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid-token :as cts-mitreid-token]))


(def ^:const service "session-mitreid-token")


;;
;; resource
;;

(def ^:const resource
  {:service     service
   :name        "OIDC Token Authentication Configuration"
   :description "OpenID Connect Token Authentication Configuration"
   :instance    "authn-name"
   :client-ips  ["127.0.0.1"]})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-mitreid-token/schema))


(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Configuration template
;;

(defn initialize
  []
  (p/register resource))
