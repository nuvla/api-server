(ns sixsq.nuvla.server.resources.session-template-github
  "
Resource that is used to create a session with GitHub authentication.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.session-template :as p]
    [sixsq.nuvla.server.resources.spec.session-template-github :as st-github]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const authn-method "github")


(def ^:const resource-name "GitHub")


(def ^:const resource-url authn-method)

;;
;; initialization: register this Session template
;;
(defn initialize
  []
  (p/register authn-method)
  (std-crud/initialize p/resource-url ::st-github/schema)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-github/schema)))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-github/schema))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))
