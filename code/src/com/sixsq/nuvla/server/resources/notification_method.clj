(ns com.sixsq.nuvla.server.resources.notification-method
  "
Collection for holding notification method configurations.
"
  (:require
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.event-config :as ec]
    [com.sixsq.nuvla.server.resources.common.event-context :as ectx]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.notification-method :as notif-method-schema]
    [com.sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})

;;
;; Events
;;

(defmethod ec/events-enabled? resource-type
  [_resource-type]
  true)


(defmethod ec/log-event? (str resource-type ".edit")
  [_event _response]
  true)

(defmethod ec/log-event? (str resource-type ".test")
  [_event _response]
  true)

(defmethod ec/log-event? "test.notification"
  [_event _response]
  true)


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::notif-method-schema/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::notif-method-schema/schema)
  (md/register resource-metadata))


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::notif-method-schema/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [request]
  (let [resp (add-impl request)]
    (ka-crud/publish-on-add resource-type resp)
    resp))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (let [resp (edit-impl request)]
    (ka-crud/publish-on-edit resource-type resp)
    resp))


(defn throw-references-exist
  [resource-id collections request]
  (doseq [collection collections]
    (let [filter     (format "method-ids='%s'" resource-id)
          authn-info (auth/current-authentication request)
          req        {:cimi-params {:filter (parser/parse-cimi-filter filter)
                                    :last   0}
                      :params      {:resource-name collection}
                      :nuvla/authn authn-info}
          count      (-> (crud/query req)
                         :body
                         :count)]
      (when (pos? count)
        (throw (r/ex-conflict (format "References to %s exist in %s." resource-id collection)))))))

(defn integrity-check
  [resource-id collections request]
  (throw-references-exist resource-id collections request)
  resource-id)

(def collections ["subscription-config"])

(defn delete-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (let [resource-id     (str resource-type "/" uuid)
          delete-response (-> resource-id
                              (integrity-check collections request)
                              crud/retrieve-by-id-as-admin
                              (a/throw-cannot-delete request)
                              db/delete)]
      (ka-crud/publish-tombstone resource-type resource-id)
      delete-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))

;;
;; Events
;;

(def event-context-keys [:name
                         :description
                         :method
                         :destination])

(defn set-event-context
  [resource]
  (ectx/add-to-context :event-name "test.notification")
  (ectx/add-to-context :resource (select-keys resource event-context-keys)))

(def ^:const test-response-message "notification method test submitted")

(defmethod crud/do-action [resource-type "test"]
  [{{uuid :uuid} :params :as _request}]
  (try
    (let [resource (crud/retrieve-by-id-as-admin (str resource-type "/" uuid))]
      (set-event-context resource)
      (r/map-response test-response-message 201))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defn set-resource-ops
  [{:keys [id] :as resource} request]
  (let [ops (cond-> [(u/action-map id :test)]
                    (a/can-edit? resource request) (conj (u/operation-map id :edit))
                    (a/can-delete? resource request) (conj (u/operation-map id :delete)))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))

(defmethod crud/set-operations resource-type
  [resource request]
  (if (u/is-collection? resource-type)
    (crud/set-standard-collection-operations resource request)
    (set-resource-ops resource request)))
