(ns com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-mqtt
  "
Allows credentials for Mqtt services to be stored.
"
  (:require
    [com.sixsq.nuvla.auth.utils.acl :as acl-utils]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.credential-template :as p]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-mqtt :as cred-tpl-mqtt]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "infrastructure-service-mqtt")


(def ^:const resource-url credential-subtype)


(def ^:const resource-name "Mqtt Credentials")


(def ^:const method credential-subtype)


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const resource
  {:subtype           credential-subtype
   :method            method
   :name              resource-name
   :description       "Mqtt Credentials"
   :acl               resource-acl
   :resource-metadata "resource-metadata/credential-template-registry"})


;;
;; multimethods for validation
;;


(def validate-fn (u/create-spec-validation-fn ::cred-tpl-mqtt/schema))


(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this credential-template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::cred-tpl-mqtt/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::cred-tpl-mqtt/schema-create "create"))


(defn initialize
  []
  (p/register resource)
  (md/register resource-metadata)
  (md/register resource-metadata-create))
