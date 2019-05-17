(ns sixsq.nuvla.server.resources.credential-template-infrastructure-service-exoscale
  "This CredentialTemplate allows creating a Credential instance to hold
  cloud credentials for the Exoscale's services."
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-exoscale :as service]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-type "infrastructure-service-exoscale")


(def ^:const resource-name "Exoscale API keys")


(def ^:const method "store-infrastructure-service-exoscale")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const resource
  {:type                    credential-type
   :method                  method
   :name                    resource-name
   :description             "Exoscale cloud credentials"
   :exoscale-api-key        ""
   :exoscale-api-secret-key ""
   :acl                     resource-acl
   :resource-metadata       "resource-metadata/credential-template-driver-exoscale"})


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
