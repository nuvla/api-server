(ns sixsq.nuvla.server.resources.credential-infrastructure-service-swarm
  "
This resource contains the values necessary to access a Docker Swarm service.
These consist of a public 'cert' and the associated private 'key'. The
certificate authority's public certificate, 'ca', should also be provided.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm :as tpl]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-coe :as service-coe]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::service-coe/schema))


(defn initialize
  []
  (md/register resource-metadata))


;;
;; convert template to credential: just copies the necessary keys from the provided template.
;;

(defmethod p/tpl->credential tpl/credential-subtype
  [{:keys [subtype method parent ca cert key acl]} _request]
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

(def validate-fn (u/create-spec-validation-fn ::service-coe/schema))


(defmethod p/validate-subtype tpl/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::service-coe/schema-create))


(defmethod p/create-validate-subtype tpl/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; operations
;;


(defn set-resource-ops
  [{:keys [id] :as resource} request]
  (let [can-manage? (a/can-manage? resource request)
        ops         (cond-> []
                            (a/can-edit? resource request) (conj (u/operation-map id :edit))
                            (a/can-delete? resource request) (conj (u/operation-map id :delete))
                            can-manage? (conj (u/action-map id :check)))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))


(defmethod p/set-credential-operations tpl/credential-subtype
  [{:keys [resource-type] :as resource} request]
  (if (u/is-collection? resource-type)
    (crud/set-standard-collection-operations resource request)
    (set-resource-ops resource request)))

(defmethod p/post-add-hook tpl/credential-subtype
  [{:keys [id] :as _resource} request]
  (p/create-check-credential-request id request))
