(ns com.sixsq.nuvla.server.resources.configuration-template-session-mitreid
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-template :as p]
    [com.sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid :as cts-mitreid]))


(def ^:const service "session-mitreid")


;;
;; resource
;;

(def ^:const resource
  {:service          service
   :name             "MITREid Authentication Configuration"
   :description      "MITREid OpenID Connect Authentication Configuration"
   :instance         "authn-name"
   :authorize-url    "http://auth.example.com"
   :token-url        "http://token.example.com"
   :user-profile-url "http://userinfo.example.com"
   :client-id        "server-assigned-client-id"
   :client-secret    "aaabbbcccdddd"
   :public-key       "ABCDEF..."})


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
