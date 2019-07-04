(ns sixsq.nuvla.server.resources.infrastructure-service-template
  "
A collection of templates that allow users to create infrastructure-service
resources that identify other services that will be used by Nuvla, for example
Docker Swarm clusters or S3 object stores.

An ephemeral, in-memory 'database' of `infrastructure-service-template`
resources is used to store the collection. As a consequence, the filtering,
paging, etc. parameters are not supported.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))


(def collection-acl {:query ["group/nuvla-user"]})


;;
;; in-memory "database" of available service-template resources
;;


(def templates (atom {}))


(defn complete-template
  "Completes the given template with server-managed information:
   resource-type, timestamps, operations, and ACL."
  [{:keys [method] :as resource}]
  (when method
    (let [id (str resource-type "/" method)]
      (-> resource
          (merge {:id            id
                  :resource-type resource-type
                  :acl           resource-acl})
          u/update-timestamps))))


(defn register
  "Registers a given service-template resource with the server. The `resource`
   document must be valid. The `type` attribute will be used to create the id
   of the resource as 'service-template/type'."
  [resource]
  (when-let [{:keys [id] :as full-resource} (complete-template resource)]
    (try
      (crud/validate full-resource)
      (swap! templates assoc id full-resource)
      (log/info "loaded service-template" id)
      (catch Exception e
        (log/error "could not load service-template" id ":" (.getMessage e))))))


;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Dispatches the validation of the template on the `method`
           attribute."
          :method)


(defmethod validate-subtype :default
  [{:keys [method] :as resource}]
  (throw (ex-info (str "unknown service-template method '" method "'") resource)))


(defmethod crud/validate
  resource-type
  [resource]
  (validate-subtype resource))


;;
;; CRUD operations: only retrieve and query are supported
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
  (let [wrapper-fn              (std-crud/collection-wrapper-fn resource-type collection-acl collection-type true false)
        entries                 (or (filter #(a/can-view? % request) (vals @templates)) [])
        ;; FIXME: At least the paging options should be supported.
        options                 (select-keys request [:query-params :cimi-params])
        count-before-pagination (count entries)
        wrapped-entries         (wrapper-fn request entries)
        entries-and-count       (assoc wrapped-entries :count count-before-pagination)]
    (r/json-response entries-and-count)))


;;
;; initialization: create metadata for this collection
;;

(defn initialize
  []
  (md/register (gen-md/generate-metadata ::ns ::infra-service-tpl/schema)))

