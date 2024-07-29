(ns com.sixsq.nuvla.server.resources.session-template-mitreid
  "
Resource that is used to create a session using a the standard OIDC workflow
from a MITREid server.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.session-template :as p]
    [com.sixsq.nuvla.server.resources.spec.session-template-mitreid :as st-mitreid]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const authn-method "mitreid")


(def ^:const resource-name "MITREid")


(def ^:const resource-url authn-method)


;;
;; initialization: register this session-template
;;
(defn initialize
  []
  (p/register authn-method)
  (std-crud/initialize p/resource-type ::st-mitreid/schema)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-mitreid/schema)))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-mitreid/schema))


(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))
