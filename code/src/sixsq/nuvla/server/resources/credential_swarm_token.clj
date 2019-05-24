(ns sixsq.nuvla.server.resources.credential-swarm-token
  "
Token for either a worker or master node of a Swarm cluster.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-swarm-token :as tpl-swarm-token]
    [sixsq.nuvla.server.resources.spec.credential-swarm-token :as swarm-token-spec]
    [sixsq.nuvla.server.resources.spec.credential-template-swarm-token :as ct-swarm-token-spec]))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::swarm-token-spec/schema))


;;
;; convert template to credential
;;

(defmethod p/tpl->credential tpl-swarm-token/credential-subtype
  [{:keys [subtype method scope token parent acl]} request]
  [nil (cond-> {:resource-type p/resource-type
                :subtype       subtype
                :method        method
                :scope         scope
                :token         token}
               acl (assoc :acl acl)
               parent (assoc :parent parent))])


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::swarm-token-spec/schema))


(defmethod p/validate-subtype tpl-swarm-token/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::ct-swarm-token-spec/schema-create))


(defmethod p/create-validate-subtype tpl-swarm-token/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; operations
;;

(defn set-collection-ops
  [{:keys [id] :as resource} request]
  (if (a/can-add? resource request)
    (let [ops [{:rel (:add c/action-uri) :href id}]]
      (assoc resource :operations ops))
    (dissoc resource :operations)))


(defn set-resource-ops
  [{:keys [id] :as resource} request]
  (let [can-manage? (a/can-manage? resource request)
        ops         (cond-> []
                            (a/can-edit? resource request) (conj {:rel (:edit c/action-uri) :href id})
                            (a/can-delete? resource request) (conj {:rel (:delete c/action-uri) :href id}))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))


(defmethod p/set-credential-operations tpl-swarm-token/credential-subtype
  [{:keys [resource-type] :as resource} request]
  (if (u/is-collection? resource-type)
    (set-collection-ops resource request)
    (set-resource-ops resource request)))
