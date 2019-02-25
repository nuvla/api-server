(ns sixsq.nuvla.server.resources.credential-template-driver-exoscale
    "This CredentialTemplate allows creating a Credential instance to hold
    cloud credentials for the Exoscale's Docker Machine driver."
  (:require
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.resources.spec.credential-template-driver-exoscale :as driver]))


(def ^:const credential-type "cloud-driver-cred-exoscale")


(def ^:const resource-name "Exoscale driver API keys")


(def ^:const resource-url credential-type)


(def ^:const method "store-cloud-driver-cred-exoscale")


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
   :name                    "docker-machine-driver-exo"
   :description             "Driver exoscale for docker machine"
   :exoscale-api-key        ""
   :exoscale-api-secret-key ""
   :acl                     resource-acl
   :resourceMetadata        "resource-metadata/credential-template-driver-exoscale"})


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