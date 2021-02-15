(ns sixsq.nuvla.server.resources.infrastructure-service
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
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic :as infra-srvc-gen]
    [sixsq.nuvla.server.resources.subscription :as subs]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})

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
  [{:keys [method] :as resource}]
  [{:status 500, :message (str "invalid service resource implementation '" method "'")} nil])


;;
;; multimethod for a post-add hook
;;

(defmulti post-add-hook
  (fn [service request]
    (:method service)))


;; default post-add hook is a no-op
(defmethod post-add-hook :default
  [service request]
  nil)


;;
;; CRUD operations
;;


(defmulti set-crud-operations
  (fn [resource request]
    (:method resource)))


(defmethod set-crud-operations :default
  [resource request]
  (crud/set-standard-operations resource request))


(defmethod crud/set-operations resource-type
  [resource request]
  (set-crud-operations resource request))


(defmulti do-action-stop
  (fn [resource request]
    (:method resource)))


(defmethod do-action-stop :default
  [_ _])


(defmethod crud/do-action [resource-type "stop"]
  [{{uuid :uuid} :params :as request}]
  (let [resource (crud/retrieve-by-id-as-admin (str resource-type "/" uuid))]
    (do-action-stop resource request)) )



(defmulti do-action-start
          (fn [resource request]
            (:method resource)))


(defmethod do-action-start :default
  [_ _])


(defmethod crud/do-action [resource-type "start"]
  [{{uuid :uuid} :params :as request}]
  (let [resource (crud/retrieve-by-id-as-admin (str resource-type "/" uuid))]
    (do-action-start resource request)) )


(defmulti do-action-terminate
          (fn [resource request]
            (:method resource)))


(defmethod do-action-terminate :default
  [_ _])


(defmethod crud/do-action [resource-type "terminate"]
  [{{uuid :uuid} :params :as request}]
  (let [resource (crud/retrieve-by-id-as-admin (str resource-type "/" uuid))]
    (do-action-terminate resource request)) )


(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


;; requires a service-template to create a new service
(defmethod crud/add resource-type
  [request]

  ;; name, description, and tags values are taken from
  ;; the create wrapper, NOT the contents of :template
  (let [authn-info         (auth/current-authentication request)
        body               (:body request)
        desc-attrs         (u/select-desc-keys body)
        validated-template (-> body
                               (assoc :resource-type create-type)
                               (std-crud/resolve-hrefs authn-info true)
                               (update-in [:template] merge desc-attrs) ;; validate desc attrs
                               crud/validate
                               :template)
        service            (tpl->service validated-template)

        response           (add-impl (assoc request :body service))
        id                 (-> response :body :resource-id)
        service            (assoc service :id id)]
    (post-add-hook service request)
    response))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(defn event-state-change
  [{current-state :state id :id} {{new-state :state} :body :as request}]
  (if (and new-state (not (= current-state new-state)))
           (event-utils/create-event id new-state
                                     (a/default-acl (auth/current-authentication request))
                                     :severity "low"
                                     :category "state")))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{{uuid :uuid} :params {new-state :state} :body :as request}]
  (let [id (str resource-type "/" uuid)
        resource (if (boolean new-state) (db/retrieve id request))
        ret (edit-impl request)]
    (try
      (if (and (= 200 (:status ret)) resource)
        (event-state-change resource request))
      (catch Exception e
        (log/errorf "Failed creating event on state change of %s with %s" id e)))
    ret))


(def delete-impl (std-crud/delete-fn resource-type))


(defmulti delete
  (fn [resource request]
    (:method resource)))


(defmethod delete :default
  [resource request]
  (delete-impl request))


(defn post-delete-hooks
  [{{uuid :uuid} :params :as request} delete-resp]
  (let [id (str resource-type "/" uuid)]
    (if (= 200 (:status delete-resp))
      (do (event-utils/create-event id "DELETED"
                                (a/default-acl (auth/current-authentication request))
                                :severity "low"
                                :category "state")
          (subs/delete-individual-subscriptions id request)))))

(defmethod crud/delete resource-type
  [{{uuid :uuid} :params :as request}]
  (let [resource (db/retrieve (str resource-type "/" uuid) request)
        delete-resp (delete resource request)]
    (post-delete-hooks request delete-resp)
    delete-resp))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))
