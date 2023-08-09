(ns sixsq.nuvla.server.resources.event
  "
The `event` resources provide a timestamp and other information when some
event occurs. These are used, for example, to mark changes in the lifecycle of
an application.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.events.config :as events-config]
    [sixsq.nuvla.events.impl :as events]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.event :as event]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user" "group/nuvla-anon"]
                     :bulk-delete ["group/nuvla-user"]})


;;
;; "Implementations" of multimethod declared in crud namespace
;;

(def validate-fn (u/create-spec-validation-fn ::event/schema))


(defmethod crud/validate
  resource-type
  [resource]
  (validate-fn resource))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


(defn can-view-resource?
  [{{{href :href} :resource} :body :as request}]
  (when (some? href) (crud/retrieve-by-id href request))
  request)


(defmethod crud/add resource-type
  [request]
  (events/add request))


(defmethod crud/retrieve resource-type
  [request]
  (events/retrieve request))


(defmethod crud/delete resource-type
  [request]
  (events/delete request))


;;
;; available operations
;;

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (if (u/is-collection? resource-type)
    (if (a/can-add? resource request)
      (let [ops [(u/operation-map id :add)]]
        (assoc resource :operations ops))
      (dissoc resource :operations))
    (if (a/can-edit? resource request)
      (let [ops [(u/operation-map id :delete)]]
        (assoc resource :operations ops))
      (dissoc resource :operations))))


;;
;; collection
;;


(defmethod crud/query resource-type
  [request]
  (events/query request))


(def bulk-delete-impl (std-crud/bulk-delete-fn resource-type collection-acl collection-type))


(defmethod crud/bulk-delete resource-type
  [request]
  (bulk-delete-impl request))

;;
;; events configuration
;;


;; disable events for the `event` resource
(defmethod events-config/events-enabled? resource-type [_]
  false)


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::event/schema))


;; Initialize the event resource before all others, because some
;; resources call crud operations during initialization, which can generates events.
(def initialization-order 0)


(defn initialize
  []
  (std-crud/initialize resource-type ::event/schema)
  (md/register resource-metadata))

