(ns sixsq.nuvla.server.resources.common.crud
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.response :as r]))

;;
;; CRUD dispatch functions
;;

(defn resource-name-dispatch
  [request]
  (get-in request [:params :resource-name]))


(defn resource-id-dispatch
  [resource-id & _]
  (first (u/split-resource-id resource-id)))


(defn resource-name-and-action-dispatch
  [request]
  ((juxt :resource-name :action) (:params request)))


;;
;; Primary CRUD multi-methods
;;

(defmulti add resource-name-dispatch)


(defmethod add :default
  [request]
  (throw (r/ex-bad-method request)))


(defmulti query resource-name-dispatch)


(defmethod query :default
  [request]
  (throw (r/ex-bad-method request)))


(defmulti retrieve resource-name-dispatch)


(defmethod retrieve :default
  [request]
  (throw (r/ex-bad-method request)))

(defmulti retrieve-by-id resource-id-dispatch)


(defmethod retrieve-by-id :default
  [resource-id & [options]]
  (db/retrieve resource-id (or options {})))


(defn retrieve-by-id-as-admin
  "Calls the retrieve-by-id multimethod with options that set the user
   identity to the administrator to allow access to any resource. Works around
   the authentication enforcement at the database level."
  [resource-id]
  (let [opts {:nuvla/authn auth/internal-identity}]
    (retrieve-by-id resource-id opts)))


(defmulti edit resource-name-dispatch)


(defmethod edit :default
  [request]
  (throw (r/ex-bad-method request)))


(defmulti delete resource-name-dispatch)


(defmethod delete :default
  [request]
  (throw (r/ex-bad-method request)))


(defmulti do-action resource-name-and-action-dispatch)


(defmethod do-action :default
  [request]
  (throw (r/ex-bad-action request (resource-name-and-action-dispatch request))))


;;
;; Resource schema validation.
;;

(defmulti validate
          "Validates the given resource, returning the resource itself on success.
           This method dispatches on the value of resource-type.  For any unknown
           dispatch value, the method throws an exception."
          :resource-type)


(defmethod validate :default
  [resource]
  (throw (ex-info (str "unknown resource type: " (:resource-type resource)) (or resource {}))))


;;
;; Provide allowed operations for resources and collections
;;

(defmulti set-operations
          "Adds the authorized resource operations to the resource based on the current
           user and the resource's ACL.  Dispatches on the value of resource-type.
           For any unregistered resource-type, the default implementation will add the
           'add' action for a Collection and the 'edit' and 'delete' actions for resources,
           if the current user has the MODIFY right."
          :resource-type)


(defn set-standard-collection-operations
  [{:keys [id] :as resource} request]
  (if (a/can-add? resource request)
    (let [ops [{:rel (:add c/action-uri) :href id}]]
      (assoc resource :operations ops))
    (dissoc resource :operations)))


(defn set-standard-resource-operations
  [{:keys [id] :as resource} request]
  (let [ops (cond-> []
                    (a/can-edit? resource request) (conj {:rel (:edit c/action-uri) :href id})
                    (a/can-delete? resource request) (conj {:rel (:delete c/action-uri) :href id}))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))


(defn set-standard-operations
  [{:keys [resource-type] :as resource} request]
  (if (u/is-collection? resource-type)
    (set-standard-collection-operations resource request)
    (set-standard-resource-operations resource request)))


(defmethod set-operations :default
  [resource request]
  (set-standard-operations resource request))


;;
;; Determine the identifier for a new resource.
;; This is normally a random UUID, but may require
;; specialization, for example using the username for
;; user resources.
;;

(defmulti new-identifier
          (fn [json resource-name]
            resource-name))


(defmethod new-identifier :default
  [json resource-name]
  (assoc json :id (u/new-resource-id resource-name)))


;;
;; Determine the ACL to use for a new resource.
;; The default is to leave the :acl key blank.
;;

(defmulti add-acl
          (fn [{:keys [resource-type]} request]
            resource-type))


(defmethod add-acl :default
  [json request]
  json)
