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
    [sixsq.nuvla.server.resources.spec.timeseries :as timeseries]
    [sixsq.nuvla.server.resources.timeseries.data-utils :as data-utils]
    [sixsq.nuvla.server.resources.timeseries.utils :as utils]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user"]
                     :bulk-action ["group/nuvla-user"]})

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

;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl (dissoc resource :acl) request))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type
                               :validate-fn validate))

(defmethod crud/add resource-type
  [request]
  (let [{status :status {:keys [resource-id]} :body :as response} (add-impl request)]
    (when (= 201 status)
      (-> (crud/retrieve-by-id-as-admin resource-id)
          (utils/create-timeseries)))
    response))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))

(def edit-impl (std-crud/edit-fn resource-type))

(defmethod crud/edit resource-type
  [{{uuid :uuid} :params :as request}]
  (let [current (-> (str resource-type "/" uuid)
                    crud/retrieve-by-id-as-admin
                    (a/throw-cannot-edit request))
        resp    (-> request
                    (utils/throw-dimensions-can-only-be-appended current)
                    (utils/throw-metrics-can-only-be-added current)
                    edit-impl)]
    (utils/edit-timeseries (:body resp))
    resp))

(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (let [{:keys [status] :as response} (delete-impl request)]
    (when (= 200 status)
      (utils/delete-timeseries (u/request->resource-id request)))
    response))

;;
;; insert/bulk insert datapoints actions
;;

(defmethod crud/do-action [resource-type utils/action-insert]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id               (str resource-type "/" uuid)
          timeseries-index (utils/resource-id->timeseries-index id)
          timeseries       (-> (crud/retrieve-by-id-as-admin id)
                               (a/throw-cannot-manage request))]
      (->> body
           (utils/add-timestamp)
           (utils/validate-datapoint timeseries)
           (db/add-timeseries-datapoint timeseries-index)))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type utils/action-bulk-insert]
  [{{uuid :uuid} :params body :body :as request}]
  (std-crud/throw-bulk-header-missing request)
  (try
    (let [id               (str resource-type "/" uuid)
          timeseries-index (utils/resource-id->timeseries-index id)
          timeseries       (-> (crud/retrieve-by-id-as-admin id)
                               (a/throw-cannot-manage request))]
      (->> body
           (map utils/add-timestamp)
           (utils/validate-datapoints timeseries)
           (db/bulk-insert-timeseries-datapoints timeseries-index))
      (r/map-response "bulk insert of timeseries datapoints executed successfully" 200))
    (catch Exception e
      (or (ex-data e) (throw e)))))

;;
;; data query action
;;

(defmethod crud/do-action [resource-type utils/action-data]
  [{{uuid :uuid :as body} :body :keys [params] :as request}]
  (try
    (data-utils/wrapped-query-data params request)
    (catch Exception e
      (or (ex-data e) (throw e)))))

;;
;; available operations
;;

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [insert-op      (u/action-map id utils/action-insert)
        bulk-insert-op (u/action-map id utils/action-bulk-insert)
        data-op        (u/action-map id utils/action-data)
        can-manage?    (a/can-manage? resource request)]
    (assoc resource
      :operations
      (cond-> []
              can-manage?
              (conj insert-op bulk-insert-op data-op)))))


;;
;; collection
;;

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))

(defmethod crud/query resource-type
  [request]
  (query-impl request))

(defn initialize
  []
  (std-crud/initialize resource-type ::timeseries/schema))
