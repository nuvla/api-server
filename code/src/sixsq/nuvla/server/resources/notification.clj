(ns sixsq.nuvla.server.resources.notification
  "
TODO: provide namespace documentation.

Notification resource.

ACL

Notifications can only be created by admins. Creator of notification
should provide resource level ACL accordingly, which for example
may depend on the type of the notification.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.notification :as notification]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-admin"]})

(def resource-acl {:owners ["group/nuvla-admin"]})

;;
;; "Implementations" of multimethod declared in crud namespace
;;

(def validate-fn (u/create-spec-validation-fn ::notification/schema))


(defmethod crud/validate
  resource-type
  [resource]
  (validate-fn resource))


(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (if (u/is-collection? resource-type)
    (assoc resource :acl collection-acl)
    (assoc resource :acl (merge resource-acl (-> request :body :acl)))))


;;
;; available collection and resource operations
;;


(defn set-collection-ops
  [{:keys [id] :as resource} request]
  (if (a/can-add? resource request)
    (let [ops [{:rel (:add c/action-uri) :href id}]]
      (assoc resource :operations ops))
    (dissoc resource :operations)))


(defn set-resource-ops
  [{:keys [id] :as resource} request]
  (let [can-manage? (a/can-manage? resource request)
        ops (cond-> []
                    can-manage? (conj {:rel (:delete c/action-uri) :href id})
                    can-manage? (conj {:rel (:defer c/action-uri) :href (str id "/defer")}))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))


(defmethod crud/set-operations resource-type
  [resource request]
  (if (u/is-collection? resource-type)
    (set-collection-ops resource request)
    (set-resource-ops resource request)))


(defmethod crud/do-action [resource-type "defer"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)
          hide-until (-> request :body :hide-until)]
      (-> id
          (db/retrieve request)
          (a/throw-cannot-manage request)
          (assoc :hide-until hide-until)
          (crud/validate)
          (db/edit request))
      (r/map-response (str id " hidden util " hide-until) 200 id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; collection
;;

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [{{:keys [orderby]} :cimi-params :as request}]
  (query-impl (assoc-in request [:cimi-params :orderby] (if (seq orderby) orderby [["updated" :desc]]))))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::notification/schema)
  (md/register (gen-md/generate-metadata ::ns ::notification/schema)))
