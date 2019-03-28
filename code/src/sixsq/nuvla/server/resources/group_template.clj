(ns sixsq.nuvla.server.resources.group-template
  "
Templates for creating a new group. The collection contains a single template
(group-template/generic) that serves as a placeholder. It is not needed for
creating a group and does not provide any useful defaults.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.group-template :as group-tpl]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def resource-acl {:owners ["group/nuvla-admin"]})


(def collection-acl {:query ["group/nuvla-admin"]
                     :add   ["group/nuvla-admin"]})


;;
;; resource
;;

(def ^:const resource
  {:id               (str resource-type "/generic")
   :name             "Create Group"
   :description      "used to create a new group"
   :acl              resource-acl
   :resourceMetadata "resource-metadata/group-template-generic"})


;;
;; atom to keep track of the group-template resources (only 1 for now)
;;

(def templates (atom {}))


(defn complete-resource
  "Completes the document with the resource-type and timestamps."
  [resource]
  (-> resource
      (merge {:resource-type resource-type})
      u/update-timestamps))


(defn register
  "Registers a group-template with the server."
  [resource]
  (when-let [{:keys [id] :as full-resource} (complete-resource resource)]
    (try
      (crud/validate full-resource)
      (swap! templates assoc id full-resource)
      (log/info "loaded group-template" id)
      (catch Exception e
        (log/error "invalid group-template:" resource)))))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::group-tpl/schema))


(defmethod crud/validate
  resource-type
  [resource]
  (validate-fn resource))


;;
;; CRUD operations
;;

(defmethod crud/retrieve resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (get @templates id)
          (a/can-view-acl? request)
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


(defn- viewable? [request {:keys [acl] :as entry}]
  (try
    (a/can-view-acl? {:acl acl} request)
    (catch Exception _
      false)))


(defmethod crud/query resource-type
  [request]
  (a/throw-cannot-query collection-acl request)
  (let [wrapper-fn (std-crud/collection-wrapper-fn resource-type collection-acl collection-type false false)
        entries (or (filter (partial viewable? request) (vals @templates)) [])
        ;; FIXME: At least the paging options should be supported.
        options (select-keys request [:user-id :claims :query-params :cimi-params])
        count-before-pagination (count entries)
        wrapped-entries (wrapper-fn request entries)
        entries-and-count (assoc wrapped-entries :count count-before-pagination)]
    (r/json-response entries-and-count)))


;;
;; initialization: create metadata for this collection
;;

(defn initialize
  []
  (register resource)
  (md/register (gen-md/generate-metadata ::ns ::group-tpl/schema)))
