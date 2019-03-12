(ns sixsq.nuvla.server.resources.credential-infrastructure-service-minio
  "Provides the credentials necessary to access a Minio S3 service."
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-minio :as cred-tpl-mino]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-minio :as cred-tpl-mino-spec]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-minio :as cred-minio]))


;;
;; convert template to credential
;;

(defmethod cred/tpl->credential cred-tpl-mino/credential-type
  [{:keys [type method access-key secret-key infrastructure-services acl]} request]
  (let [resource (cond-> {:resource-type           cred/resource-type
                          :type                    type
                          :method                  method
                          :access-key              access-key
                          :secret-key              secret-key
                          :infrastructure-services infrastructure-services}
                         acl (assoc :acl acl))]
    [nil resource]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cred-minio/schema))


(defmethod cred/validate-subtype cred-tpl-mino/credential-type
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::cred-tpl-mino-spec/schema-create))


(defmethod cred/create-validate-subtype cred-tpl-mino/credential-type
  [resource]
  (create-validate-fn resource))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize cred/resource-type ::cred-minio/schema))
