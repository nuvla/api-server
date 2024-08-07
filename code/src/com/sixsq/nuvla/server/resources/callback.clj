(ns com.sixsq.nuvla.server.resources.callback
  "
Deferred actions that must be triggered by a user or other external agent.
For example, callbacks can used for email validation.

Each callback represents a single, atomic action that must be triggered by an
external agent. The action is identified by the `action` attribute. Some
actions may require state information, which may be provided in the `data`
attribute. Each action is implemented as a sub-resource of the generic
callback.

All callback resources support the CIMI `execute` action, which triggers the
action of the callback. The state of the callback will indicate the success or
failure of the action.

Generally, these resources are created by CIMI server resources rather than
end-users. Anyone with the URL of the callback can trigger the `execute`
action. Consequently, the callback id should securely communicated to
appropriate users.
"
  (:require
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.server.resources.callback.utils :as utils]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.callback :as callback]
    [com.sixsq.nuvla.server.util.log :as log-util]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-admin"]
                     :add         ["group/nuvla-admin"]
                     :bulk-delete ["group/nuvla-admin"]})


;;
;; validate subclasses of callbacks
;;

(def validate-fn (u/create-spec-validation-fn ::callback/schema))
(defmethod crud/validate
  resource-type
  [resource]
  (validate-fn resource))

;;
;; multimethod for ACLs
;;

(defn create-acl []
  {:owners ["group/nuvla-admin"]})


(defmethod crud/add-acl resource-type
  [{:keys [acl] :as resource} _request]
  (assoc
    resource
    :acl
    (or acl (create-acl))))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))
(defmethod crud/add resource-type
  [request]
  (add-impl (assoc-in request [:body :state] "WAITING")))


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


(def bulk-delete-impl (std-crud/bulk-delete-fn resource-type collection-acl collection-type))
(defmethod crud/bulk-delete resource-type
  [request]
  (bulk-delete-impl request))

;;
;; available operations
;;

(defmethod crud/set-operations resource-type
  [{:keys [id resource-type] :as resource} request]
  (let
    [collection? (u/is-collection? resource-type)
     can-delete? (a/can-delete? resource request)
     can-add?    (a/can-add? resource request)
     ops         (cond-> []
                         (and collection? can-add?) (conj (u/operation-map id :add))
                         (and (not collection?) can-delete?) (conj (u/operation-map id :delete))
                         (and (not collection?)
                              (utils/executable? resource)) (conj (u/action-map id :execute)))]
    (if (empty? ops)
      (dissoc resource :operations)
      (assoc resource :operations ops))))

;;
;; actions
;;

(defn action-dispatch
  [callback-resource _request]
  (:action callback-resource))


(defmulti execute action-dispatch)


(defmethod execute :default
  [{:keys [id] :as callback-resource} request]
  (utils/callback-failed! id)
  (let [msg (format "error executing callback: '%s' of type '%s'"
                    id (action-dispatch callback-resource request))]
    (log-util/log-and-throw 400 msg)))


(defmethod crud/do-action [resource-type "execute"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (when-let [callback-resource (crud/retrieve-by-id-as-admin id)]
        (if (utils/executable? callback-resource)
          (execute callback-resource request)
          (r/map-response "cannot re-execute callback" 409 id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; general utility for creating a new callback in other resources
;;

(defn create
  "Creates a callback resource with the given action-name, base-uri, target
   resource, data (optional), expires (optional).
   Returns the URL to trigger the callback's action."
  [action-name base-uri href & {:keys [data expires tries-left]}]
  (let [callback-request {:params      {:resource-name resource-type}
                          :body        (cond-> {:action          action-name
                                                :target-resource {:href href}}
                                               data (assoc :data data)
                                               expires (assoc :expires expires)
                                               tries-left (assoc :tries-left tries-left))
                          :nuvla/authn auth/internal-identity}
        {{:keys [resource-id]} :body status :status} (crud/add callback-request)]

    (if (= 201 status)
      (if-let [callback-resource (crud/set-operations
                                   (crud/retrieve-by-id-as-admin resource-id) {})]
        (if-let [validate-op (u/get-op callback-resource "execute")]
          (str base-uri validate-op)
          (let [msg "callback does not have execute operation"]
            (throw (ex-info msg (r/map-response msg 500 resource-id)))))
        (let [msg "cannot retrieve user create callback"]
          (throw (ex-info msg (r/map-response msg 500 resource-id)))))
      (let [msg "cannot create user callback"]
        (throw (ex-info msg (r/map-response msg 500 "")))))))


;;
;; initialization: common schema for all subtypes
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::callback/schema))

(defn initialize
  []
  (std-crud/initialize resource-type ::callback/schema)
  (md/register resource-metadata))
