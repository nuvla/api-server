(ns com.sixsq.nuvla.server.resources.subscription-config
  "
Collection for holding subscriptions configurations.
"
  (:require
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.subscription-config :as subs-schema]
    [com.sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [com.sixsq.nuvla.server.util.log :as log]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::subs-schema/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::subs-schema/schema)
  (md/register resource-metadata))


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::subs-schema/schema))


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

;; :reset-start-date must not be provided when :reset-interval is Xd
(defn valid-reset-start-date-vs-interval?
  [subs-conf]
  (not (and
         (and
           (contains? (:criteria subs-conf) :reset-interval)
           (contains? (:criteria subs-conf) :reset-start-date))
         (re-matches #"^[1-9][0-9]{0,2}d$" (get-in subs-conf [:criteria :reset-interval])))))
(def ^:const err-msg-reset-start-date-vs-interval
  ":reset-start-date must not be provided when :reset-interval is Xd")


(def consistency-validators
  [{:validator valid-reset-start-date-vs-interval?
    :err-msg err-msg-reset-start-date-vs-interval}])


(defn validate-consistency
  [subs-conf]
  (doseq [{validator :validator err-msg :err-msg} consistency-validators]
    (when-not (validator subs-conf)
      (log/log-and-throw-400 err-msg)))
  subs-conf)


(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (validate-consistency (:body request))
  (let [resp (add-impl request)]
    (ka-crud/publish-on-add resource-type resp :key "id")
    resp))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(def edit-forbidden-attrs
  [:resource-filter :resource-kind])


(defn remove-forbidden-attrs
  [body]
  (apply dissoc body edit-forbidden-attrs))


(defn edit-subs-config
  [request]
  (let [new-body (-> (:body request)
                     remove-forbidden-attrs
                     validate-consistency)
        req-updated (assoc request :body new-body)
        response (edit-impl req-updated)]
    (ka-crud/publish-on-edit resource-type response)
    response))


(defn set-enabled
  "'enabled' boolean"
  [request enabled]
  (edit-subs-config (assoc request :body {:enabled enabled})))


(defn disable-impl
  [request]
  (set-enabled request false))


(defn enable-impl
  [request]
  (set-enabled request true))


(defn set-notif-method-ids-impl
  [request]
  (edit-subs-config (assoc request :body {:method-ids (-> request
                                                          :body
                                                          :method-ids)})))

(defmethod crud/edit resource-type
  [request]
  (edit-subs-config request))


(defn delete-impl
  [{{uuid :uuid} :params :as request}]
  (let [resp (disable-impl request)]
    (when-not (= 200 (:status resp))
      (throw (ex-info "Delete precondition failed." resp))))
  (try
    (let [id (str resource-type "/" uuid)
          delete-response (-> (crud/retrieve-by-id-as-admin id)
                              (a/throw-cannot-delete request)
                              db/delete)]
      (ka-crud/publish-tombstone resource-type id)
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


;; Actions

(def ^:const enable "enable")
(def ^:const disable "disable")
(def ^:const set-notif-method-ids "set-notif-method-ids")

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [can-manage? (a/can-manage? resource request)
        enable-op (u/action-map id enable)
        disable-op (u/action-map id disable)
        method-ids-op (u/action-map id set-notif-method-ids)]
    (cond-> (crud/set-standard-operations resource request)
            can-manage? (update :operations concat [enable-op
                                                    disable-op
                                                    method-ids-op]))))

;; Action to enable subscription to notifications.

(defmethod crud/do-action [resource-type enable]
  [request]
  (enable-impl request))


;; Action to disable subscription to notifications.

(defmethod crud/do-action [resource-type disable]
  [request]
  (disable-impl request))

;; Action to set notification method id.

(defmethod crud/do-action [resource-type set-notif-method-ids]
  [request]
  (set-notif-method-ids-impl request))
