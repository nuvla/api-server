(ns sixsq.nuvla.server.resources.callback
  "
Deferred actions that must be triggered by a user or other external agent.
For example, used for email validation.

Each callback represents a single, atomic action that must be triggered by an
external agent. The action is identified by the `action` attribute. Some
actions may require state information, which may be provided in the `data`
attribute.

All callback resources support the CIMI `execute` action, which triggers the
action of the callback. The state of the callback will indicate the success or
failure of the action.

Generally, these resources are created by CIMI server resources rather than
end-users. Anyone with the URL of the callback can trigger the `execute`
action. Consequently, the callback id should only be communicated to
appropriate users.
"
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.callback.utils :as utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.callback :as callback]
    [sixsq.nuvla.server.util.log :as log-util]
    [sixsq.nuvla.util.response :as r]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.resources.resource-metadata :as md]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const collection-name (u/ns->collection-type *ns*))

(def ^:const collection-uri collection-name)

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

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
  {:owner {:principal "ADMIN"
           :type      "ROLE"}
   :rules [{:principal "ADMIN"
            :type      "ROLE"
            :right     "VIEW"}]})


(defmethod crud/add-acl resource-type
  [{:keys [acl] :as resource} request]
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


(def query-impl (std-crud/query-fn resource-type collection-acl collection-uri))
(defmethod crud/query resource-type
  [request]
  (query-impl request))

;;
;; available operations
;;

(defmethod crud/set-operations resource-type
  [{:keys [id resource-type] :as resource} request]
  (let [href (str id "/execute")
        collection? (u/cimi-collection? resource-type)
        modifiable? (a/modifiable? resource request)
        ops (cond-> []
                    (and collection? modifiable?) (conj {:rel (:add c/action-uri) :href id})
                    (and (not collection?) modifiable?) (conj {:rel (:delete c/action-uri) :href id})
                    (and (not collection?) (utils/executable? resource)) (conj {:rel (:execute c/action-uri) :href href}))]
    (if (empty? ops)
      (dissoc resource :operations)
      (assoc resource :operations ops))))

;;
;; actions
;;

(defn action-dispatch
  [callback-resource request]
  (:action callback-resource))


(defmulti execute action-dispatch)


(defmethod execute :default
  [{:keys [id] :as callback-resource} request]
  (utils/callback-failed! id)
  (let [msg (format "error executing callback: '%s' of type '%s'" id (action-dispatch callback-resource request))]
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

;; FIXME: Fix ugliness around needing to create ring requests with authentication!
(defn create
  "Creates a callback resource with the given action-name, baseURI, target
   resource, data (optional). Returns the URL to trigger the callback's action."
  ([action-name baseURI href]
   (create action-name baseURI href nil))
  ([action-name baseURI href data]
   (let [callback-request {:params   {:resource-name resource-type}
                           :body     (cond-> {:action         action-name
                                              :targetResource {:href href}}
                                             data (assoc :data data))
                           :identity {:current         "INTERNAL"
                                      :authentications {"INTERNAL" {:identity "INTERNAL"
                                                                    :roles    ["ADMIN"]}}}}
         {{:keys [resource-id]} :body status :status} (crud/add callback-request)]

     (if (= 201 status)
       (if-let [callback-resource (crud/set-operations (crud/retrieve-by-id-as-admin resource-id) {})]
         (if-let [validate-op (u/get-op callback-resource "execute")]
           (str baseURI validate-op)
           (let [msg "callback does not have execute operation"]
             (throw (ex-info msg (r/map-response msg 500 resource-id)))))
         (let [msg "cannot retrieve user create callback"]
           (throw (ex-info msg (r/map-response msg 500 resource-id)))))
       (let [msg "cannot create user callback"]
         (throw (ex-info msg (r/map-response msg 500 ""))))))))


;;
;; initialization: common schema for all subtypes
;;
(defn initialize
  []
  (std-crud/initialize resource-type ::callback/schema)
  (md/register (gen-md/generate-metadata ::ns ::callback/schema)))
