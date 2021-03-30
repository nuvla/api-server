(ns sixsq.nuvla.server.resources.nuvlabox-cluster
  "
The `nuvlabox-cluster` resource represents a cluster of at least one NuvlaBox
"
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-cluster :as nb-cluster]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [clojure.pprint :refer [pprint]]
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
;; acl
;;


(defmethod crud/add-acl resource-type
  [resource request]
  (when-let [nuvlabox-ids (concat (:nuvlabox-managers resource) (:nuvlabox-workers resource))]
    (let [acls       (utils/get-nuvlabox-acls nuvlabox-ids)
          edit-acl   (into [] (distinct (apply concat (mapv :edit-acl acls))))
          manage     (distinct (concat (apply concat (mapv :manage acls)) edit-acl))
          view-data  (distinct (concat (apply concat (mapv :view-data acls)) edit-acl))
          acl        {:owners    ["group/nuvla-admin"]
                      :delete    ["group/nuvla-admin"]
                      :manage    (into [] manage)
                      :view-data (into [] view-data)
                      :edit-acl  edit-acl}]
      (assoc resource :acl acl))))


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
  [{{:keys [workers managers
            nuvlabox-workers nuvlabox-managers]
     :as   body} :body :as request}]
  (let [nb-workers  (if workers
                      (utils/get-matching-nuvlaboxes workers)
                      nuvlabox-workers)
        nb-managers (if managers
                      (utils/get-matching-nuvlaboxes managers)
                      nuvlabox-managers)
        new-body    (apply assoc body
                      (apply concat
                        (filter second
                          (partition 2 [:nuvlabox-workers nb-workers
                                        :nuvlabox-managers nb-managers]))))]
    (add-impl (assoc request :body new-body))))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


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
