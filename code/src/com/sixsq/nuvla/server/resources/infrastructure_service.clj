(ns com.sixsq.nuvla.server.resources.infrastructure-service
  "
This resource represents an infrastructure service with an endpoint. Instances
of a `infrastructure-service` resource must reference an
`infrastructure-service-group` resource via the `parent` attribute. Associated
credentials should make an explicit reference to the relevant
`infrastructure-service` resources.

This is a templated resource. All creation requests must be done via an
existing `infrastructure-service-template` resource.
"
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.event-config :as ec]
    [com.sixsq.nuvla.server.resources.common.event-context :as ectx]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.infrastructure-service :as infra-service]
    [com.sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic :as infra-srvc-gen]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})

;;
;; Events
;;


(defmethod ec/events-enabled? resource-type
  [_resource-type]
  true)

(defmethod ec/log-event? "infrastructure-service.add"
  [_event _response]
  true)

(defmethod ec/log-event? "infrastructure-service.edit"
  [_event _response]
  true)

(defmethod ec/log-event? "infrastructure-service.delete"
  [_event _response]
  true)


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::infra-service/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::infra-srvc-gen/schema)
  (md/register resource-metadata))


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::infra-srvc-gen/schema))


(defmulti validate-subtype :subtype)


(defmethod validate-subtype :default
  [resource]
  (validate-fn resource))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))


;;
;; validate create requests for service resources
;;

(defn dispatch-on-method [resource]
  (get-in resource [:template :method]))


(defmulti create-validate-subtype dispatch-on-method)


(defmethod create-validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown service create type: " (dispatch-on-method resource)) resource)))


(defmethod crud/validate create-type
  [resource]
  (create-validate-subtype resource))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; template processing multimethod
;;
;; dispatches on the :method key, must return created service
;; resource from template
;;

(defmulti tpl->service :method)


;;
;; All concrete service types MUST provide an implementation of this
;; multimethod. The default implementation will throw an 'internal
;; server error' exception.
;;

(defmethod tpl->service :default
  [{:keys [method] :as _resource}]
  [{:status 500, :message (str "invalid service resource implementation '" method "'")} nil])


;;
;; multimethod for a post-add hook
;;

(defmulti post-add-hook
          (fn [service _request]
            (:method service)))


;; default post-add hook is a no-op
(defmethod post-add-hook :default
  [_service _request]
  nil)


;;
;; CRUD operations
;;


(defmethod crud/set-operations resource-type
  [resource request]
  (crud/set-standard-operations resource request))


(defmulti do-action-stop
          (fn [resource _request]
            (:method resource)))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


;; requires a service-template to create a new service
(defmethod crud/add resource-type
  [request]

  ;; name, description, and tags values are taken from
  ;; the create wrapper, NOT the contents of :template
  (let [authn-info (auth/current-authentication request)
        body       (:body request)
        desc-attrs (u/select-desc-keys body)
        service    (-> body
                       (assoc :resource-type create-type)
                       (std-crud/resolve-hrefs authn-info true)
                       (update-in [:template] merge desc-attrs) ;; validate desc attrs
                       crud/validate
                       :template
                       tpl->service)
        response   (add-impl (assoc request :body service))
        id         (-> response :body :resource-id)
        service    (assoc service :id id)]
    (post-add-hook service request)
    response))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(defn event-state-change
  [{_current-state :state _id :id} {{_new-state :state} :body :as _request}]
  ;; legacy events
  #_(when (and new-state (not (= current-state new-state)))
    (event-utils/create-event id new-state
                              (a/default-acl (auth/current-authentication request))
                              :severity "low"
                              :category "state")))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{{uuid :uuid} :params {new-state :state} :body :as request}]
  (let [id       (str resource-type "/" uuid)
        resource (when (boolean new-state) (crud/retrieve-by-id-as-admin id))
        ret      (edit-impl request)]
    (try
      (when (and (= 200 (:status ret)) resource)
        (event-state-change resource request))
      (catch Exception e
        (log/errorf "Failed creating event on state change of %s with %s" id e)))
    ret))

(defn post-delete-hooks
  [{{_uuid :uuid} :params :as _request} _delete-resp]
  ;; legacy events
  #_(let [id (str resource-type "/" uuid)]
    (when (= 200 (:status delete-resp))
      (event-utils/create-event id "DELETED"
                                (a/default-acl (auth/current-authentication request))
                                :severity "low"
                                :category "state"))))

(def delete-impl (std-crud/delete-fn resource-type))

(defmethod crud/delete resource-type
  [{{uuid :uuid} :params :as request}]
  (let [resource    (db/retrieve (str resource-type "/" uuid) request)
        delete-resp (delete-impl request)]
    (ectx/add-to-context :resource resource)
    (ectx/add-to-context :acl (:acl resource))
    (post-delete-hooks request delete-resp)
    delete-resp))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))
