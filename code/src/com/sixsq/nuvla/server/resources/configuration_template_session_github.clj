(ns com.sixsq.nuvla.server.resources.configuration-template-session-github
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-template :as p]
    [com.sixsq.nuvla.server.resources.spec.configuration-template-session-github :as cts-github]))


(def ^:const service "session-github")


;;
;; resource
;;

(def ^:const resource
  {:service       service
   :name          "GitHub Authentication Configuration"
   :description   "GitHub Authentication Configuration"
   :instance      "authn-name"
   :client-id     "github-oauth-application-client-id"
   :client-secret "github-oauth-application-client-secret"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-github/schema))


(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Configuration template
;;

(defn initialize
  []
  (p/register resource))
