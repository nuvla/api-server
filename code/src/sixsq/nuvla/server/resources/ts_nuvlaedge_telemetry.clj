(ns sixsq.nuvla.server.resources.ts-nuvlaedge-telemetry
  "
The `ts-nuvlaedge` resources create a timeseries related to nuvlaedge.
"
  (:require
    [sixsq.nuvla.db.es.common.utils :as escu]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.ts-nuvlaedge-telemetry :as ts-nuvlaedge-telemetry]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-admin"]
                     :add   ["group/nuvla-admin"]})


;;
;; "Implementations" of multimethod declared in crud namespace
;;


(def validate-fn (u/create-spec-validation-fn ::ts-nuvlaedge-telemetry/schema))

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
  [resource _request]
  resource)

(def add-impl (std-crud/add-timeseries-datapoint-fn resource-type collection-acl resource-type
                                                    :validate-fn validate
                                                    :options {:refresh false}))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


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


(def bulk-insert-impl (std-crud/bulk-insert-timeseries-datapoints-fn
                        (escu/collection-id->index resource-type)
                        collection-acl collection-type))

(defmethod crud/bulk-action [resource-type "bulk-insert"]
  [request]
  (validate-metrics (:body request))
  (bulk-insert-impl request))

(defn initialize
  []
  (std-crud/initialize-as-timeseries resource-type ::ts-nuvlaedge-telemetry/schema))
