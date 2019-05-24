(ns sixsq.nuvla.server.resources.group
  "
This resource represents a group of users. The unique identifier for the group
is a kebab-case string, provided when the group is created. All group names
that start with 'nuvla-' are reserved for the server.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.group :as group]
    [sixsq.nuvla.server.resources.spec.group-template :as group-tpl]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-admin"]})


;;
;; validate functions
;;

(def validate-fn (u/create-spec-validation-fn ::group/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::group-tpl/schema-create))


(defmethod crud/validate create-type
  [resource]
  (create-validate-fn resource))


;;
;; multimethod for ACLs
;;

;; forces update of acl to have admin as owner and all users can view metadata
(defmethod crud/add-acl resource-type
  [resource request]
  (assoc resource :acl {:owners    ["group/nuvla-admin"]
                        :view-meta ["group/nuvla-user"]}))


;;
;; "Implementations" of multimethod declared in crud namespace
;;

(defn tpl->group
  [{:keys [group-identifier] :as resource}]
  (let [id (str resource-type "/" group-identifier)]
    (-> resource
        (dissoc :group-identifier)
        (assoc :id id
               :users []))))


;; modified to retain id and not call new-identifier
(defn add-impl [{:keys [body] :as request}]
  (a/throw-cannot-add collection-acl request)
  (let [id (:id body)]
    (db/add
      resource-type
      (-> body
          u/strip-service-attrs
          (assoc :id id
                 :resource-type resource-type)
          u/update-timestamps
          (crud/add-acl request)
          crud/validate)
      {})))


(defmethod crud/add resource-type
  [{:keys [body] :as request}]
  (a/throw-cannot-add collection-acl request)
  (let [authn-info (auth/current-authentication request)
        desc-attrs (u/select-desc-keys body)
        body       (-> body
                       (assoc :resource-type create-type)
                       (std-crud/resolve-hrefs authn-info)
                       (update-in [:template] merge desc-attrs) ;; validate desc attrs
                       (crud/validate)
                       :template
                       tpl->group)]
    (add-impl (assoc request :body body))))


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


;;
;; collection
;;

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::group/schema)
  (md/register (gen-md/generate-metadata ::ns ::group/schema))

  (std-crud/add-if-absent (str resource-type "/nuvla-admin") resource-type
                          {:name        "Nuvla Administrator Group"
                           :description "group of users with server administration rights"
                           :template    {:group-identifier "nuvla-admin"}})
  (std-crud/add-if-absent (str resource-type "/nuvla-user") resource-type
                          {:name        "Nuvla Authenticated Users"
                           :description "pseudo-group of users that have been authenticated"
                           :template    {:group-identifier "nuvla-user"}})
  (std-crud/add-if-absent (str resource-type "/nuvla-anon") resource-type
                          {:name        "Nuvla Anonymous Users"
                           :description "pseudo-group of all users authenticated or not"
                           :template    {:group-identifier "nuvla-anon"}}))
