(ns sixsq.nuvla.server.resources.timeseries
  "
The `timeseries` resources represent a timeseries.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.timeseries :as timeseries]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-admin"]
                     :add   ["group/nuvla-admin"]})


;;
;; "Implementations" of multimethod declared in crud namespace
;;


(def validate-fn (u/create-spec-validation-fn ::timeseries/schema))

(defn validate
  [resource]
  (validate-fn resource))

(defmethod crud/validate resource-type
  [resource]
  (validate resource))

(defn validate-metrics
  [metrics]
  (doseq [metric metrics]
    (validate metric)))

;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl (dissoc resource :acl) request))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type
                               :validate-fn validate))

(defn resource-id->timeseries-id
  [resource-id]
  (str "ts-" (u/id->uuid resource-id)))

(defn dimension->es-property
  [{:keys [field-name field-type]}]
  [field-name {:type                  field-type
               :time_series_dimension true}])

(defn metric->es-property
  [{:keys [field-name field-type metric-type]}]
  [field-name {:type               field-type
               :time_series_metric metric-type}])

(defn ts-resource->mappings
  [{:keys [dimensions metrics]}]
  {:properties
   (into {"@timestamp" {:type   "date"
                        :format "strict_date_optional_time||epoch_millis"}}
         (concat
           (map dimension->es-property dimensions)
           (map metric->es-property metrics)))})

(defn ts-resource->routing-path
  [{:keys [dimensions]}]
  (mapv :field-name dimensions))

(defn create-timeseries
  [resource-id]
  (let [resource     (crud/retrieve-by-id-as-admin resource-id)
        mappings     (ts-resource->mappings resource)
        routing-path (ts-resource->routing-path resource)]
    (db/create-timeseries
      (resource-id->timeseries-id resource-id)
      {:mappings     mappings
       :routing-path routing-path})))

(defmethod crud/add resource-type
  [request]
  (let [{{:keys [resource-id]} :body :as response} (add-impl request)]
    (create-timeseries resource-id)
    response))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


;;
;; available operations
;;

(defmethod crud/set-operations resource-type
  [resource _request]
  resource)


;;
;; collection
;;

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


(def bulk-insert-impl (std-crud/bulk-insert-metrics-fn resource-type collection-acl collection-type))

(defmethod crud/bulk-action [resource-type "bulk-insert"]
  [request]
  (validate-metrics (:body request))
  (bulk-insert-impl request))

(defn initialize
  []
  (std-crud/initialize resource-type ::timeseries/schema))
