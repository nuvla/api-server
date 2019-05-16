(ns sixsq.nuvla.server.resources.credential-infrastructure-service-swarm
  "
This resource contains the values necessary to access a Docker Swarm service.
These consist of a public 'cert' and the associated private 'key'. The
certificate authority's public certificate, 'ca', must also be provided.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as cred]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm :as tpl]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-swarm :as service-swarm]))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize cred/resource-type ::service-swarm/schema))


;;
;; convert template to credential: just copies the necessary keys from the provided template.
;;

(defmethod cred/tpl->credential tpl/credential-type
  [{:keys [type method parent ca cert key]} request]
  [nil (cond-> {:resource-type cred/resource-type
                :type          type
                :method        method
                :ca            ca
                :cert          cert
                :key           key}
               parent (assoc :parent parent))])


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::service-swarm/schema))


(defmethod cred/validate-subtype tpl/credential-type
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::service-swarm/schema-create))


(defmethod cred/create-validate-subtype tpl/credential-type
  [resource]
  (create-validate-fn resource))
