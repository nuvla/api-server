(ns com.sixsq.slipstream.ssclj.resources.session-template-mitreid
  "
Resource that is used to create a session using a the standard OIDC workflow
from a MITREid server.
"
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.session-template :as p]
    [com.sixsq.slipstream.ssclj.resources.spec.session-template-mitreid :as st-mitreid]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]))


(def ^:const authn-method "mitreid")


(def ^:const resource-name "MITREid")


(def ^:const resource-url authn-method)


;;
;; description
;;
(def ^:const desc
  p/SessionTemplateDescription)

;;
;; initialization: register this Session template
;;
(defn initialize
  []
  (p/register authn-method desc)
  (std-crud/initialize p/resource-url ::st-mitreid/schema)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-mitreid/schema)))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-mitreid/schema))
(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))
