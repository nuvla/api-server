(ns sixsq.nuvla.server.resources.credential-template-infrastructure-service-google
  "
Allows `docker-machine` credentials for Google to be created. The attribute
names correspond exactly to those required by `docker-machine`.
"
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-google :as service]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "infrastructure-service-google")


(def ^:const resource-name "GCE service account private key")


(def ^:const resource-url credential-subtype)


(def ^:const method "store-infrastructure-service-google")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const resource
  {:subtype           credential-subtype
   :method            method
   :name              resource-name
   :description       "GCE service account credentials"
   :project-id        "my-project-id"
   :private-key-id    "abcde1234"
   :private-key       "-----BEGIN PRIVATE KEY-----\\nMIIaA0n\\n-----END PRIVATE KEY-----\\n"
   :client-email      "1234-compute@developer.gserviceaccount.com"
   :client-id         "98765"
   :acl               resource-acl
   :resource-metadata "resource-metadata/credential-template-driver-gce"})


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

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::service/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::service/schema-create "create"))


(defn initialize
  []
  (p/register resource)
  (md/register resource-metadata)
  (md/register resource-metadata-create))
