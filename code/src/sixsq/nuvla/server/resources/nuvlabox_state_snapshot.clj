(ns sixsq.nuvla.server.resources.nuvlabox-state-snapshot
  (:require
    [sixsq.nuvla.auth.utils :as auth-utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.nuvlabox-state :as nuvlabox-state]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:add   ["group/nuvla-admin"]
                     :query ["group/nuvla-user"]})


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::nuvlabox-state/nuvlabox-state))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; CRUD operations
;;

(def create-snapshot (std-crud/add-fn resource-type collection-acl resource-type))


(defn create-nuvlabox-state-snapshot
  [nuvlabox-state]
  (let [nuvlabox-state-snapshot-request {:params      {:resource-name resource-type}
                                         :nuvla/authn auth-utils/internal-identity
                                         :body        (assoc nuvlabox-state :resource-type resource-type)}]
    (create-snapshot nuvlabox-state-snapshot-request)))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::nuvlabox-state/nuvlabox-state))
