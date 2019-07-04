(ns sixsq.nuvla.server.resources.credential-infrastructure-service-swarm
  "
This resource contains the values necessary to access a Docker Swarm service.
These consist of a public 'cert' and the associated private 'key'. The
certificate authority's public certificate, 'ca', should also be provided.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm :as tpl]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-swarm :as service-swarm]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.resources.resource-metadata :as md]))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::service-swarm/schema)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::service-swarm/schema)))


;;
;; convert template to credential: just copies the necessary keys from the provided template.
;;

(defmethod p/tpl->credential tpl/credential-subtype
  [{:keys [subtype method parent ca cert key acl]} request]
  [nil (cond-> {:resource-type p/resource-type
                :subtype       subtype
                :method        method
                :ca            ca
                :cert          cert
                :key           key}
               acl (assoc :acl acl)
               parent (assoc :parent parent))])


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::service-swarm/schema))


(defmethod p/validate-subtype tpl/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::service-swarm/schema-create))


(defmethod p/create-validate-subtype tpl/credential-subtype
  [resource]
  (create-validate-fn resource))
