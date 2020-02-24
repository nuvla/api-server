(ns sixsq.nuvla.server.resources.credential-template-infrastructure-service-registry
  "
Allows credentials for Docker registry services to be stored.
"
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-registry :as cred-tpl-registry]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "infrastructure-service-registry")


(def ^:const resource-url credential-subtype)


(def ^:const resource-name "Docker Registry Credentials")


(def ^:const method "infrastructure-service-registry")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const resource
  {:subtype           credential-subtype
   :method            method
   :name              resource-name
   :description       "Docker Registry Credentials"
   :acl               resource-acl
   :resource-metadata "resource-metadata/credential-template-registry"})


;;
;; multimethods for validation
;;


(def validate-fn (u/create-spec-validation-fn ::cred-tpl-registry/schema))


(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this credential-template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::cred-tpl-registry/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::cred-tpl-registry/schema-create "create"))


(defn initialize
  []
  (p/register resource)
  (md/register resource-metadata)
  (md/register resource-metadata-create))