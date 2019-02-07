(ns sixsq.nuvla.server.resources.configuration-template
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.configuration-template]
    [sixsq.nuvla.util.response :as r]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const resource-name "ConfigurationTemplate")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "ConfigurationTemplateCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "ADMIN"
                            :type      "ROLE"
                            :right     "VIEW"}]})

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "VIEW"}]})

;;
;; atom to keep track of the loaded ConfigurationTemplate resources
;;
(def templates (atom {}))

(defn complete-resource
  "Completes the given document with server-managed information:
   resourceURI, timestamps, operations, and ACL."
  [{:keys [service] :as resource}]
  (when service
    (let [id (str resource-url "/" service)]
      (-> resource
          (merge {:id          id
                  :resourceURI resource-uri
                  :acl         resource-acl})
          u/update-timestamps))))

(defn register
  "Registers a given ConfigurationTemplate resource and its description
   with the server.  The resource document (resource) and the description
   (desc) must be valid.  The key will be used to create the id of
   the resource as 'configuration-template/key'."
  [resource]
  (when-let [full-resource (complete-resource resource)]
    (let [id (:id full-resource)]
      (swap! templates assoc id full-resource)
      (log/info "loaded ConfigurationTemplate" id))))

;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given resource against the specific
           ConfigurationTemplate subtype schema."
          :service)

(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown ConfigurationTemplate type: " (:service resource)) resource)))

(defmethod crud/validate
  resource-uri
  [resource]
  (validate-subtype resource))

;;
;; CRUD operations
;;

(defmethod crud/add resource-name
  [request]
  (throw (r/ex-bad-method request)))

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

(defmethod crud/delete resource-name
  [request]
  (throw (r/ex-bad-method request)))

(defmethod crud/query resource-name
  [request]
  (a/can-view? {:acl collection-acl} request)
  (let [wrapper-fn (std-crud/collection-wrapper-fn resource-name collection-acl collection-uri false false)
        ;; FIXME: At least the paging options should be supported.
        options (select-keys request [:identity :query-params :cimi-params :user-name :user-roles])
        [count-before-pagination entries] ((juxt count vals) @templates)
        wrapped-entries (wrapper-fn request entries)
        entries-and-count (assoc wrapped-entries :count count-before-pagination)]
    (r/json-response entries-and-count)))


