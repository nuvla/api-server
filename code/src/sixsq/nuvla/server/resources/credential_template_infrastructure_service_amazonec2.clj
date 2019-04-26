(ns sixsq.nuvla.server.resources.credential-template-infrastructure-service-amazonec2
  "This CredentialTemplate allows creating a Credential instance to hold
  cloud credentials for the AWS's services."
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-amazonec2 :as service]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.auth.utils.acl :as acl-utils]))


(def ^:const credential-type "infrastructure-service-amazonec2")


(def ^:const resource-name "AWS API keys")


(def ^:const method "store-infrastructure-service-amazonec2")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const resource
  {:type                    credential-type
   :method                  method
   :name                    resource-name
   :description             "AWS credentials"
   :amazonec2-access-key    ""
   :amazonec2-secret-key    ""
   :infrastructure-services []
   :acl                     resource-acl
   :resource-metadata       "resource-metadata/credential-template-driver-aws"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::service/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(defn initialize
  []
  (p/register resource)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::service/schema))
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::service/schema-create "create")))
