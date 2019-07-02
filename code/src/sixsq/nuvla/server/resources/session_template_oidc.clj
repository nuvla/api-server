(ns sixsq.nuvla.server.resources.session-template-oidc
  "
Resource that is used to create a session using the standard OIDC workflow.
Intended for OIDC servers implemented with Keycloak.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.session-template :as p]
    [sixsq.nuvla.server.resources.spec.session-template-oidc :as st-oidc]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const authn-method "oidc")


(def ^:const resource-name "OIDC")


(def ^:const resource-url authn-method)


;;
;; initialization: register this session-template
;;

(defn initialize
  []
  (p/register authn-method)
  (std-crud/initialize p/resource-type ::st-oidc/schema)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-oidc/schema)))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-oidc/schema))


(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))
