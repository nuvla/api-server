(ns sixsq.nuvla.server.resources.configuration-template
  "
The configuration-template resources allow administrators to provide
configuration information for the micro-services of the Nuvla platform.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.configuration-template :as ct]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def resource-acl (acl-utils/normalize-acl {:owners ["group/nuvla-admin"]}))


(def collection-acl {:query ["group/nuvla-admin"]
                     :add   ["group/nuvla-admin"]})


;;
;; atom to keep track of the loaded ConfigurationTemplate resources
;;
(def templates (atom {}))

(defn complete-resource
  "Completes the given document with server-managed information:
   resource-type, timestamps, operations, and ACL."
  [{:keys [service] :as resource}]
  (when service
    (let [id (str resource-type "/" service)]
      (-> resource
          (merge {:id            id
                  :resource-type resource-type
                  :acl           resource-acl})
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
;; initialization: create metadata for this collection
;;

(defn initialize
  []
  (md/register (gen-md/generate-metadata ::ns ::ct/schema)))


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
  resource-type
  [resource]
  (validate-subtype resource))

;;
;; CRUD operations
;;

(defmethod crud/add resource-type
  [request]
  (throw (r/ex-bad-method request)))

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

(defmethod crud/delete resource-type
  [request]
  (throw (r/ex-bad-method request)))

(defmethod crud/query resource-type
  [request]
  (a/throw-cannot-query collection-acl request)
  (let [wrapper-fn        (std-crud/collection-wrapper-fn resource-type collection-acl collection-type false false)
        ;; FIXME: At least the paging options should be supported.
        options           (select-keys request [:user-id :claims :query-params :cimi-params])
        [count-before-pagination entries] ((juxt count vals) @templates)
        wrapped-entries   (wrapper-fn request entries)
        entries-and-count (assoc wrapped-entries :count count-before-pagination)]
    (r/json-response entries-and-count)))


