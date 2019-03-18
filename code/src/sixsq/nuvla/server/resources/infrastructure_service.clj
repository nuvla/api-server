(ns sixsq.nuvla.server.resources.infrastructure-service
  "
This resource represents an infrastructure service with an endpoint. Instances
of a infrastructure-service resource must reference a
infrastructure-service-group resource via the `parent` attribute. Associated
credentials should make an explicit reference to the relevant
infrastructure-service resources.

This is a templated resource. All creation requests must be done via an
existing infrastructure-service-template resource.
"
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::infra-service/schema)
  (md/register (gen-md/generate-metadata ::ns ::infra-service/schema)))


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::infra-service/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;;

(defmulti validate-subtype :type)


(defmethod validate-subtype :default
           [resource]
           (logu/log-and-throw-400 (str "unknown infrastructure service type: '" resource (:type resource) "'")))


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
  (a/add-acl (dissoc resource :acl) request))


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
          (fn [service template]
            (:method service)))


;; default post-add hook is a no-op
(defmethod post-add-hook :default
  [service template]
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
  (let [idmap {:identity (:identity request)}
        body (:body request)
        desc-attrs (u/select-desc-keys body)
        validated-template (-> body
                               (assoc :resource-type create-type)
                               (std-crud/resolve-hrefs idmap true)
                               (update-in [:template] merge desc-attrs) ;; validate desc attrs
                               crud/validate
                               :template)
        service (tpl->service validated-template)]
    (let [response (add-impl (assoc request :body service))
          id (-> response :body :resource-id)
          service (assoc service :id id)]
      (post-add-hook service validated-template)
      response)))


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
