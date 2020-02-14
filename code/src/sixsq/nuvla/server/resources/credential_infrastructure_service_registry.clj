(ns sixsq.nuvla.server.resources.credential-infrastructure-service-registry
  "
Provides the credentials necessary to access a Docker Registry service.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-registry
     :as cred-tpl-registry]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-registry :as cred-registry]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-registry
     :as cred-tpl-registry-spec]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


;;
;; convert template to credential
;;

(defmethod p/tpl->credential cred-tpl-registry/credential-subtype
  [{:keys [subtype method username password parent acl]} request]
  (let [resource (cond-> {:resource-type p/resource-type
                          :subtype       subtype
                          :method        method
                          :username      username
                          :password      password
                          :parent        parent}
                         acl (assoc :acl acl))]
    [nil resource]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cred-registry/schema))


(defmethod p/validate-subtype cred-tpl-registry/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::cred-tpl-registry-spec/schema-create))


(defmethod p/create-validate-subtype cred-tpl-registry/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::cred-registry/schema))


(defn initialize
  []
  (std-crud/initialize p/resource-type ::cred-registry/schema)
  (md/register resource-metadata))
