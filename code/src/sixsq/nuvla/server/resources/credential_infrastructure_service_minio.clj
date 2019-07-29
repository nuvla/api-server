(ns sixsq.nuvla.server.resources.credential-infrastructure-service-minio
  "
Provides the credentials necessary to access a Minio S3 service.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-minio :as cred-tpl-mino]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-minio :as cred-minio]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-minio :as cred-tpl-mino-spec]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


;;
;; convert template to credential
;;

(defmethod p/tpl->credential cred-tpl-mino/credential-subtype
  [{:keys [subtype method access-key secret-key parent acl]} request]
  (let [resource (cond-> {:resource-type p/resource-type
                          :subtype       subtype
                          :method        method
                          :access-key    access-key
                          :secret-key    secret-key
                          :parent        parent}
                         acl (assoc :acl acl))]
    [nil resource]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cred-minio/schema))


(defmethod p/validate-subtype cred-tpl-mino/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::cred-tpl-mino-spec/schema-create))


(defmethod p/create-validate-subtype cred-tpl-mino/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::cred-minio/schema))


(defn initialize
  []
  (std-crud/initialize p/resource-type ::cred-minio/schema)
  (md/register resource-metadata))
