(ns sixsq.nuvla.server.resources.infrastructure
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.log :as logu]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


;; only authenticated users can view and create
(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})


(defmulti validate-subtype :type)

(defmethod validate-subtype :default
  [resource]
  (logu/log-and-throw-400 (str "unknown infrastructure type: '" resource (:type resource) "'")))

(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))


(defn dispatch-on-registration-method [resource]
  (get-in resource [:template :type]))

(defmulti create-validate-subtype dispatch-on-registration-method)

(defmethod create-validate-subtype :default
  [resource]
  (logu/log-and-throw-400 (str "cannot validate InfrastructureTemplate create document with type: '"
                               (dispatch-on-registration-method resource) "'")))

(defmethod crud/validate create-type
  [resource]
  (create-validate-subtype resource))

;;
;; multimethod for ACLs
;;

(defn create-acl
  [id]
  {:owner {:principal "ADMIN"
           :type      "ROLE"}
   :rules [{:principal id
            :type      "USER"
            :right     "MODIFY"}]})

(defmethod crud/add-acl resource-type
  [{:keys [acl] :as resource} request]
  (if acl
    resource
    (let [user-id (:identity (a/current-authentication request))]
      (assoc resource :acl (create-acl user-id)))))

;;
;; template processing
;;

(defn dispatch-conversion
  [resource _]
  (:type resource))

(defmulti tpl->infra dispatch-conversion)

;; default implementation throws if the credential type is unknown
(defmethod tpl->infra :default
  [resource request]
  (logu/log-and-throw-400
    (str "cannot transform InfrastructureTemplate document to template for type: '" (:type resource) "'")))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

;;
;; available operations
;;


(defn check-credential-exists
  "Use ADMIN role as we only want to check if href points to an existing
  resource."
  [body idmap]
  (let [admin {:identity {:current         "internal",
                          :authentications {"internal" {:roles #{"ADMIN"}, :identity "internal"}}}}
        href (get-in body [:template :credential])]
    (std-crud/resolve-hrefs href admin))
  body)

(defn resolve-hrefs
  [body idmap]
  (let [credential-href (if (contains? (:template body) :credential)
                          {:credential (get-in body [:template :credential])}
                          {})]                              ;; to put back the unexpanded href after
    (-> body
        (check-credential-exists idmap)
        (update-in [:template] dissoc :credential)
        (std-crud/resolve-hrefs idmap)
        (update-in [:template] merge credential-href))))

;; requires a InfrastructureTemplate to create new Infrastructure
(defmethod crud/add resource-type
  [{:keys [body] :as request}]
  (let [idmap {:identity (:identity request)}
        desc-attrs (u/select-desc-keys body)
        [create-resp {:keys [id] :as body}]
        (-> body
            (assoc :resource-type create-type)
            (update-in [:template] dissoc :type)            ;; forces use of template reference
            (resolve-hrefs idmap)
            (update-in [:template] merge desc-attrs)        ;; ensure desc attrs are validated
            crud/validate
            :template
            (tpl->infra request))]
    (-> request
        (assoc :id id :body (merge body desc-attrs))
        add-impl
        (update-in [:body] merge create-resp))))

(defmulti special-edit dispatch-conversion)

(defmethod special-edit :default
  [resource _]
  resource)

(def edit-impl (std-crud/edit-fn resource-type))
(defmethod crud/edit resource-type
  [{{uuid :uuid} :params body :body :as request}]
  (let [type (-> (str resource-type "/" uuid)
                 (db/retrieve request)
                 :type)
        new-body (-> body
                     (assoc :type type)
                     (special-edit request))]
    (edit-impl (assoc request :body new-body))))

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


;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize resource-type nil))
