(ns sixsq.nuvla.server.resources.external-object-template
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.util.response :as r]))


(def ^:const resource-tag :externalObjectTemplates)

(def ^:const resource-name "ExternalObjectTemplate")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "ExternalObjectTemplateCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "ADMIN"
                            :type      "ROLE"
                            :right     "VIEW"}
                           {:principal "USER"
                            :type      "ROLE"
                            :right     "VIEW"}
                           ]})

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "VIEW"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})

;;
;; Resource defaults
;;

(def external-object-reference-attrs-defaults
  {})


;;
;; Template validation
;;

(defmulti validate-subtype-template :objectType)

(defmethod validate-subtype-template :default
  [resource]
  (throw (ex-info (str "unknown External object template type: '" (:objectType resource) "'") resource)))

(defmethod crud/validate resource-uri
  [resource]
  (validate-subtype-template resource))


;;
;; atom to keep track of the loaded ExternalObjectTemplate resources
;;
;;
(def templates (atom {}))
(def name->kw (atom {}))


(defn collection-wrapper-fn
  "Specialized version of this function that removes the adding
   of operations to the collection and entries.  These are already
   part of the stored resources."
  [resource-name collection-acl collection-uri collection-key]
  (fn [_ entries]
    (let [skeleton {:acl         collection-acl
                    :resourceURI collection-uri
                    :id          (u/de-camelcase resource-name)}]
      (assoc skeleton collection-key entries))))


(defn complete-resource
  "Completes the given document with server-managed information:
   resourceURI, timestamps, operations, and ACL."
  [{:keys [objectType] :as resource}]
  (when objectType
    (let [id (str resource-url "/" objectType)]
      (-> resource
          (merge {:id          id
                  :resourceURI resource-uri
                  :acl         resource-acl})
          (merge external-object-reference-attrs-defaults)
          u/update-timestamps))))


(defn register
  "Registers a given ExternalObjectTemplate resource with the server.
   The resource document (resource) must be valid.
   The key will be used to create the id of
   the resource as 'external-object-template/key'."
  [resource & [name-kw-map]]
  (when-let [full-resource (complete-resource resource)]
    (let [id (:id full-resource)]
      (swap! templates assoc id full-resource)
      (log/info "loaded ExternalObjectTemplate" id)
      (when name-kw-map
        (swap! name->kw assoc id name-kw-map)
        (log/info "added name->kw mapping from ExternalObjectTemplate" id)))))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))


(defmethod crud/add resource-name
  [{{:keys [objectType]} :body :as request}]
  (if (get @templates objectType)
    (add-impl request)
    (throw (r/ex-bad-request (str "invalid external object type '" objectType "'")))))


(defmethod crud/retrieve resource-name
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)]
      (-> (get @templates id)
          (a/can-view? request)
          (r/json-response)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;; must override the default implementation so that the
;; data can be pulled from the atom rather than the database
(defmethod crud/retrieve-by-id resource-url
  [id]
  (try
    (get @templates id)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/edit resource-name
  [request]
  (throw (r/ex-bad-method request)))


(def delete-impl (std-crud/delete-fn resource-name))


(defmethod crud/delete resource-name
  [request]
  (delete-impl request))


(defmethod crud/query resource-name
  [request]
  (a/can-view? {:acl collection-acl} request)
  (let [wrapper-fn (collection-wrapper-fn resource-name collection-acl collection-uri resource-tag)
        ;; FIXME: At least the paging options should be supported.
        options (select-keys request [:identity :query-params :cimi-params :user-name :user-roles])
        [count-before-pagination entries] ((juxt count vals) @templates)
        wrapped-entries (wrapper-fn request entries)
        entries-and-count (assoc wrapped-entries :count count-before-pagination)]
    (r/json-response entries-and-count)))
