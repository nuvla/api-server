(ns sixsq.nuvla.server.resources.data-object-template
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))


(def collection-acl {:query ["group/nuvla-user"]})


;;
;; Resource defaults
;;

(def data-object-reference-attrs-defaults
  {})


;;
;; Template validation
;;

(defmulti validate-subtype-template :subtype)


(defmethod validate-subtype-template :default
  [{:keys [subtype] :as resource}]
  (throw (ex-info (str "unknown data-object-template subtype: '" subtype "'") resource)))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype-template resource))


;;
;; atom to keep track of the loaded data-object-template resources
;;

(def templates (atom {}))


(def name->kw (atom {}))


(defn complete-resource
  "Completes the given document with server-managed information:
   resource-type, timestamps, operations, and ACL."
  [{:keys [subtype] :as resource}]
  (when subtype
    (let [id (str resource-type "/" subtype)]
      (-> resource
          (merge {:id            id
                  :resource-type resource-type
                  :acl           resource-acl})
          (merge data-object-reference-attrs-defaults)
          u/update-timestamps))))


(defn register
  "Registers a given data-object-template resource with the server. The
   resource document (resource) must be valid. The key will be used to create
   the id of the resource as 'data-object-template/key'."
  [resource & [name-kw-map]]
  (when-let [full-resource (complete-resource resource)]
    (let [id (:id full-resource)]
      (swap! templates assoc id full-resource)
      (log/info "loaded data-object-template" id)
      (when name-kw-map
        (swap! name->kw assoc id name-kw-map)
        (log/info "added name->kw mapping from data-object-template" id)))))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [subtype]} :body :as request}]
  (if (get @templates subtype)
    (add-impl request)
    (throw (r/ex-bad-request (str "invalid data object subtype '" subtype "'")))))


(defmethod crud/retrieve resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (get @templates id)
          (a/throw-cannot-view request)
          (a/select-viewable-keys request)
          (r/json-response)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;; must override the default implementation so that the
;; data can be pulled from the atom rather than the database
(defmethod crud/retrieve-by-id resource-type
  [id]
  (try
    (get @templates id)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/edit resource-type
  [request]
  (throw (r/ex-bad-method request)))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(defmethod crud/query resource-type
  [request]
  (a/throw-cannot-query collection-acl request)
  (let [wrapper-fn (std-crud/collection-wrapper-fn resource-type collection-acl collection-type false false)
        ;; FIXME: At least the paging options should be supported.
        options (select-keys request [:user-id :claims :query-params :cimi-params])
        [count-before-pagination entries] ((juxt count vals) @templates)
        wrapped-entries (wrapper-fn request entries)
        entries-and-count (assoc wrapped-entries :count count-before-pagination)]
    (r/json-response entries-and-count)))
