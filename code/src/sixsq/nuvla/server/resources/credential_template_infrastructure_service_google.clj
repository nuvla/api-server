(ns sixsq.nuvla.server.resources.credential-template-infrastructure-service-google
  "This CredentialTemplate allows creating a Credential instance to hold
  cloud credentials for the GCE's services."
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-google :as service]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-type "infrastructure-service-google")


(def ^:const resource-name "GCE service account private key")


(def ^:const resource-url credential-type)


(def ^:const method "store-infrastructure-service-google")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const resource
  {:type                    credential-type
   :method                  method
   :name                    resource-name
   :description             "GCE service account credentials"
   :project-id              "my-project-id"
   :private-key-id          "abcde1234"
   :private-key             "-----BEGIN PRIVATE KEY-----\\nMIIaA0n\\n-----END PRIVATE KEY-----\\n"
   :client-email            "1234-compute@developer.gserviceaccount.com"
   :client-id               "98765"
   :infrastructure-services []
   :acl                     resource-acl
   :resource-metadata       "resource-metadata/credential-template-driver-gce"})


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
