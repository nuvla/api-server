(ns sixsq.nuvla.server.resources.user-params-template-exec
  (:require
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.user-params-template-exec]
    [sixsq.nuvla.server.resources.user-params-template :as p]))

(def ^:const params-type "execution")

(def ^:const resource
  {:paramsType          params-type
   :defaultCloudService ""
   :keepRunning         "on-success"
   :mailUsage           "never"
   :verbosityLevel      0
   :sshPublicKey        ""
   :timeout             30})


;;
;; initialization: register this Configuration template
;;
(defn initialize
  []
  (p/register resource))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn :cimi/user-params-template.exec))
(defmethod p/validate-subtype params-type
  [resource]
  (validate-fn resource))
