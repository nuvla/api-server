(ns sixsq.nuvla.server.resources.subscription-config
  "
Collection for holding subscriptions configurations.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.subscription :as subs]
    [sixsq.nuvla.server.resources.spec.subscription-config :as subs-schema]
    [sixsq.nuvla.server.util.kafka :as ka]
    [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]
    [clojure.string :as str]))


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
;; Utils.
;;

(defn set-attr-individual-subscriptions
  [{{uuid :uuid} :params :as request} attr]
  (let [filter (format "parent='%s'" (str resource-type "/" uuid))
        req {:cimi-params {:filter (parser/parse-cimi-filter filter)}
             :params      {:resource-name subs/resource-type}
             :nuvla/authn (auth/current-authentication request)}
        results (for [res (->> (crud/query req) :body :resources)]
                  (let [r (crud/edit {:params      {:uuid          (second (str/split (:id res) #"/"))
                                                    :resource-name subs/resource-type}
                                      :body        (merge res attr)
                                      :nuvla/authn (auth/current-authentication request)})]
                    (= 200 (:status r))))]
    (if (not (every? true? results))
      (throw (r/ex-bad-request "Failed to set state on subscriptions from subscription configuration."))
      results)))

(defn set-attribute-all
  [{{uuid :uuid} :params :as request} attr]
  (let [sub (-> (str resource-type "/" uuid)
                (db/retrieve request)
                (merge attr))
        response (crud/edit {:params      {:uuid          uuid
                                           :resource-name resource-type}
                             :body        sub
                             :nuvla/authn (auth/current-authentication request)})]
    (set-attr-individual-subscriptions request attr)
    response))


(defn set-enabled
  "'enabled' boolean"
  [{{uuid :uuid} :params :as request} enabled]
  (set-attribute-all request {:enabled enabled}))


(defn disable-all
  [request]
  (set-enabled request false))


(defn enable-all
  [request]
  (set-enabled request true))


(defn set-notif-method-id-all
  [request]
  ;; FIXME: !!!
  (let [id (-> request
               :body
               :method-id)
        attr {:method-id id}]
    (set-attribute-all request attr)))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

;; use 'resource-name' and 'filter' to find resources to create subscription for
(defn individual-resources-to-subscribe
  [resource-name filter request]
  (try
    (let [req {:cimi-params {:filter (parser/parse-cimi-filter filter)
                             :select ["id"]}
               :params      {:resource-name resource-name}
               :nuvla/authn (auth/current-authentication request)}]
      (->> (crud/query req)
           :body
           :resources
           (map #(:id %))))
    (catch Exception e
      (log/error (str "error when finding individual resources to subscribe:" e)))))


(defn add-subscription
  [sub]
  (crud/add {:params      {:resource-name subs/resource-type}
             :body        sub
             :nuvla/authn auth/internal-identity}))

;; create individual subscriptions
(defn create-subscriptions
  [request response]
  (if (= 201 (-> response :body :status))
    (let [id (-> response :body :resource-id)
          resource (db/retrieve id request)
          {:keys [resource-kind resource-filter]} resource
          parent (:id resource)
          resources-to-subscribe (individual-resources-to-subscribe resource-kind resource-filter request)]
      (if (not-empty resources-to-subscribe)
        (doseq [res resources-to-subscribe]
          (add-subscription (assoc resource :resource-id res :parent parent)))))))


(defmethod crud/add resource-type
  [request]
  (let [resp (add-impl request)]
    (create-subscriptions request resp)
    (ka-crud/publish-on-add resource-type resp :key "resource")
    resp))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(defn delete-subscriptions
  [parent-id request]
  ;; With 'parent-id' as parent
  ;; 1. Delete individual subscriptions from ES (with bulk delete)
  ;; 2. Publish tombstones to Kafka
  (let [filter (->> parent-id
                    (format "parent='%s'")
                    (parser/parse-cimi-filter))
        req {:cimi-params {:filter filter
                           :select ["id"]}
             :params      {:resource-name subs/resource-type}
             :nuvla/authn (auth/current-authentication request)}
        subs-ids (->> (crud/query req)
                      :body
                      :resources
                      (map #(:id %)))]
    (if (not-empty subs-ids)
      (let [req-with-filter (assoc request :cimi-params {:filter filter})
            req (merge-with merge req-with-filter {:headers {"bulk" true}})]
        ;; delete individual subscriptions
        (subs/bulk-delete-impl req)
        ;; publish tombstone
        (for [subs-id subs-ids]
          (ka-crud/publish-tombstone subs/resource-type subs-id)))))
  parent-id)


(defn delete-impl
  [{{uuid :uuid} :params :as request}]
  ;; First, set the subscription config in disabled mode,
  ;; which in turn, should set all the subscriptions into disabled mode.
  ;; Then, delete:
  ;; 1. individual subscriptions from ES (with bulk delete)
  ;; 2. publish tombstone to Kafka
  ;; 3. delete subscription config
  (let [resp (disable-all request)]
    (when-not (= 200 (:status resp))
      (throw (ex-info "Delete precondition failed." resp))))
  (try
    (let [resource-id (str resource-type "/" uuid)
          delete-response (-> resource-id
                              (delete-subscriptions request)
                              (db/retrieve request)
                              (a/throw-cannot-delete request)
                              (db/delete request))]
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


;; Actions

(def ^:const enable "enable")
(def ^:const disable "disable")
(def ^:const set-notif-method-id "set-notif-method-id")

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [can-manage? (a/can-manage? resource request)
        enable-op (u/action-map id enable)
        disable-op (u/action-map id disable)
        method-id-op (u/action-map id set-notif-method-id)
        ]
    (cond-> (crud/set-standard-operations resource request)
            can-manage? (update :operations concat [enable-op
                                                    disable-op
                                                    method-id-op]))))

;; Action to enable all subscription of this configuration.

(defmethod crud/do-action [resource-type enable]
  [request]
  (enable-all request))


;; Action to disable all subscription of this configuration.

(defmethod crud/do-action [resource-type disable]
  [request]
  (disable-all request))

;; Action to set notification method id.

(defmethod crud/do-action [resource-type set-notif-method-id]
  [request]
  (set-notif-method-id-all request))
