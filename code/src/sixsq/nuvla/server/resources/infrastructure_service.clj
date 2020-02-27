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
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.util.response :as r]))


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
  (std-crud/initialize resource-type ::infra-service/schema)
  (md/register resource-metadata))


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::infra-service/schema))


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

    (when (= (:subtype service) "swarm")
      (try
        (let [acl (:acl service)
              {{job-id     :resource-id
                job-status :status} :body} (job/create-job id "swarm_check"
                                                           acl
                                                           :priority 50)
              job-msg (str "starting " id " with async " job-id)]
          (when (not= job-status 201)
            (throw (r/ex-response "unable to create async job to check if swarm mode is enabled" 500 id)))
          (event-utils/create-event id job-msg acl)
          (r/map-response job-msg 202 id job-id))
        (catch Exception e
          (or (ex-data e) (throw e)))))
    (post-add-hook service request)
    response))


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
