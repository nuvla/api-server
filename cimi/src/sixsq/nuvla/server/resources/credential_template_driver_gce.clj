(ns sixsq.nuvla.server.resources.credential-template-driver-gce
    "This CredentialTemplate allows creating a Credential instance to hold
    cloud credentials for the Google Compute Engine's Docker Machine driver."
  (:require
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.resources.spec.credential-template-driver-gce :as driver]))


(def ^:const credential-type "cloud-driver-cred-gce")


(def ^:const resource-name "GCE driver service account private key")


(def ^:const resource-url credential-type)


(def ^:const method "store-cloud-driver-cred-gce")


(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "USER"
                            :type      "ROLE"
                            :right     "VIEW"}]})

;;
;; resource
;;

(def ^:const resource
  {:type                    credential-type
   :method                  method
   :name                    "docker-machine-driver-gce"
   :description             "Driver GCE for docker machine"
   :project-id              "my-project-id"
   :private-key-id          "abcde1234"
   :private-key             "-----BEGIN PRIVATE KEY-----\\nMIIaA0n\\n-----END PRIVATE KEY-----\\n"
   :client-email            "1234-compute@developer.gserviceaccount.com"
   :client-id               "98765"
   :acl                     resource-acl
   :resourceMetadata        "resource-metadata/credential-template-driver-gce"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::driver/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(defn initialize
  []
  (p/register resource)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::driver/schema)))