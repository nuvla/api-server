(ns sixsq.nuvla.server.resources.nuvlabox-cluster
  "
The `nuvlabox-cluster` resource represents a cluster of at least one NuvlaBox
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-cluster :as nb-cluster]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:add   ["group/nuvla-user"]
                     :query ["group/nuvla-user"]})

;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given nuvlabox-cluster resource against a specific
           version of the schema."
          :version)


(defmethod validate-subtype :default
  [{:keys [version] :as resource}]
  (if version
    (throw (r/ex-bad-request (str "unsupported nuvlabox-cluster version: " version)))
    (throw (r/ex-bad-request "missing nuvlabox-cluster version"))))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))


;;
;; cluster ID must be unique
;;


(defmethod crud/new-identifier resource-type
  [{:keys [cluster-id] :as cluster} resource-name]
  (->> (u/from-data-uuid cluster-id)
    (str resource-type "/")
    (assoc cluster :id)))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [{{:keys [workers managers]
     :as   body} :body :as request}]
  (let [nb-workers  (if workers
                      (utils/get-matching-nuvlaboxes workers)
                      [])
        nb-managers (utils/get-matching-nuvlaboxes managers)]
    (utils/complete-cluster-details add-impl nb-workers nb-managers request)))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{{select :select} :cimi-params {uuid :uuid} :params {:keys [workers managers]
                                                        :as   body} :body :as request}]
  (let [current    (-> (str resource-type "/" uuid)
                     (db/retrieve (assoc-in request [:cimi-params :select] nil))
                     (a/throw-cannot-edit request))
        nb-workers (if workers
                     (utils/get-matching-nuvlaboxes workers)
                     (utils/get-matching-nuvlaboxes (:workers current)))
        nb-managers (if managers
                      (utils/get-matching-nuvlaboxes managers)
                      (utils/get-matching-nuvlaboxes (:managers current)))]
    (utils/complete-cluster-details edit-impl nb-workers nb-managers request)))


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
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-cluster/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nb-cluster/schema)
  (md/register resource-metadata))
