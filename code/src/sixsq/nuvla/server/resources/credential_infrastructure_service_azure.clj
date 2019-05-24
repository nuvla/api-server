(ns sixsq.nuvla.server.resources.credential-infrastructure-service-azure
  "
Sets the service compliant attribute names and values
for Azure
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-azure :as tpl]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-azure :as service]))

;;
;; convert template to credential
;;

(defmethod p/tpl->credential tpl/credential-subtype
  [{:keys [subtype method azure-subscription-id azure-client-secret azure-client-id parent acl]} request]
  (let [resource (cond-> {:resource-type         p/resource-type
                          :subtype               subtype
                          :method                method
                          :azure-subscription-id azure-subscription-id
                          :azure-client-secret   azure-client-secret
                          :azure-client-id       azure-client-id
                          :parent                parent}
                         acl (assoc :acl acl))]
    [nil resource]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::service/schema))
(defmethod p/validate-subtype tpl/credential-subtype
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::service/schema-create))
(defmethod p/create-validate-subtype tpl/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::service/schema))
