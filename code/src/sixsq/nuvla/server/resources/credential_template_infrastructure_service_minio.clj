(ns sixsq.nuvla.server.resources.credential-template-infrastructure-service-minio
  "This credential-template resource allows credentials for Minio S3 services
   to be stored."
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-minio :as cred-tpl-minio]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-type "infrastructure-service-minio")


(def ^:const resource-name "Minio S3 Credentials")


(def ^:const method "infrastructure-service-minio")


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
   :description             "Minio S3 credentials"
   :infrastructure-services []
   :acl                     resource-acl
   :resourceMetadata        "resource-metadata/credential-template-minio"})


;;
;; multimethods for validation
;;


(def validate-fn (u/create-spec-validation-fn ::cred-tpl-minio/schema))


(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this credential-template
;;

(defn initialize
  []
  (p/register resource)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::cred-tpl-minio/schema)))
