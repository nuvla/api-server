(ns sixsq.nuvla.server.resources.credential-template-service-exoscale
  "This CredentialTemplate allows creating a Credential instance to hold
  cloud credentials for the Exoscale's services."
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-service-exoscale :as service]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-type "cloud-service-cred-exoscale")


(def ^:const resource-name "Exoscale API keys")


(def ^:const method "store-cloud-service-cred-exoscale")


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
   :name                    resource-name
   :description             "Exoscale cloud credentials"
   :exoscale-api-key        ""
   :exoscale-api-secret-key ""
   :infrastructure-services []
   :acl                     resource-acl
   :resourceMetadata        "resource-metadata/credential-template-driver-exoscale"})


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
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::service/schema)))
