(ns com.sixsq.nuvla.server.resources.session-template-mitreid-token
  "
Resource that is used to create a session using an OIDC bearer token generated
from a MITREid server. Used primarily to identify users who log from customized
portals in front of a SlipStream instance.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.session-template :as p]
    [com.sixsq.nuvla.server.resources.spec.session-template-mitreid-token :as st-mitreid-token]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const authn-method "mitreid-token")


(def ^:const resource-name "MITREid Token")


(def ^:const resource-url authn-method)


;;
;; initialization: register this session-template
;;

(defn initialize
  []
  (p/register authn-method)
  (std-crud/initialize p/resource-type ::st-mitreid-token/schema)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::st-mitreid-token/schema)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::st-mitreid-token/schema))


(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))
