(ns sixsq.nuvla.server.resources.subscription-config
  "
Collection for holding subscriptions configurations.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.subscription-config :as subs-schema]
    [sixsq.nuvla.server.util.kafka :as ka]
    [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::subs-schema/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::subs-schema/schema)
  (md/register resource-metadata))


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::subs-schema/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (let [resp (add-impl request)]
    (ka-crud/publish-on-add resource-type resp :key "resource")
    resp))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(def delete-impl (std-crud/delete-fn resource-type))

(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;; Actions

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [toggle-state-all (u/action-map id :toggle-state-all)]
    (cond-> (crud/set-standard-operations resource request)
            (a/can-manage? resource request) (update-in [:operations] conj toggle-state-all))))


;; Action to enable all subscription of a certain kind.

(defmethod crud/do-action [resource-type "enable-all"]
  [{{uuid :uuid} :params :as request}]
  ; set :state of all current subscriptions to "enabled"
  (log/warn "NotImplemented. Set state of subscriptions to enabled."))


;; Action to disable all subscription of a certain kind.

(defmethod crud/do-action [resource-type "disable-all"]
  [{{uuid :uuid} :params :as request}]
  ; set :state of all current subscriptions to "disabled"
  (log/warn "NotImplemented. Set state of subscriptions to disabled."))
