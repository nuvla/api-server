(ns sixsq.nuvla.server.resources.ts-nuvlaedge-availability
  "
The `ts-nuvlaedge` resources create a timeseries related to nuvlaedge availability.
"
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.db.es.binding :as es-binding]
    [sixsq.nuvla.server.resources.spec.ts-nuvlaedge-availability :as ts-nuvlaedge-availability]
    [sixsq.nuvla.server.resources.common.utils :as u]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-admin"]
                     :add   ["group/nuvla-admin"]})


;;
;; "Implementations" of multimethod declared in crud namespace
;;


(def validate-fn (u/create-spec-validation-fn ::ts-nuvlaedge-availability/schema))

(defn validate
  [resource]
  (validate-fn resource))

;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource _request]
  resource)

(def add-impl (std-crud/add-metric-fn resource-type collection-acl resource-type
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


(defn initialize
  []
  (std-crud/initialize-as-timeseries resource-type ::ts-nuvlaedge-availability/schema
                                     {:ilm-policy es-binding/hot-delete-policy}))

