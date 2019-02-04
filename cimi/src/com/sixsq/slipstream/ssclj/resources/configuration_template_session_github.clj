(ns com.sixsq.slipstream.ssclj.resources.configuration-template-session-github
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.configuration-template :as p]
    [com.sixsq.slipstream.ssclj.resources.spec.configuration-template-session-github :as cts-github]))


(def ^:const service "session-github")


;;
;; resource
;;

(def ^:const resource
  {:service      service
   :name         "GitHub Authentication Configuration"
   :description  "GitHub Authentication Configuration"
   :instance     "authn-name"
   :clientID     "github-oauth-application-client-id"
   :clientSecret "github-oauth-application-client-secret"})


;;
;; description
;;

(def ^:const desc
  (merge p/ConfigurationTemplateDescription
         {:clientID     {:displayName "Client ID"
                         :type        "string"
                         :description "client identifier assigned to the GitHub OAuth application"
                         :mandatory   true
                         :readOnly    false
                         :order       20}
          :clientSecret {:displayName "Client Secret"
                         :type        "password"
                         :description "client secret assigned to the GitHub OAuth application"
                         :mandatory   true
                         :readOnly    false
                         :order       21}}))


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
  (p/register resource desc))
