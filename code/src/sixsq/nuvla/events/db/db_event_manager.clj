(ns sixsq.nuvla.events.db.db-event-manager
  (:require [clojure.string :as str]
            [sixsq.nuvla.auth.acl-resource :as a]
            [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.db.filter.parser :as parser]
            [sixsq.nuvla.db.impl :as db]
            [sixsq.nuvla.events.config :as config]
            [sixsq.nuvla.events.protocol :refer [EventManager] :as p]
            [sixsq.nuvla.events.std-events :as std-events]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
            [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
            [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
            [sixsq.nuvla.server.util.log :as logu]
            [sixsq.nuvla.server.util.response :as r]
            [sixsq.nuvla.server.util.time :as time]))


;; not referencing event ns to avoid circular dependencies
(def ^:const event-resource-type "event")
(def ^:const event-collection-type "event-collection")


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user" "group/nuvla-anon"]
                     :bulk-delete ["group/nuvla-user"]})


(defn prepare-event
  "Enriches the given event with additional info. To be called before crud/add"
  [{{:keys [event-type resource timestamp severity] :as body} :body :as request}]
  (let [;; authn-info   (auth/current-authentication request)
        ;; simple-user? (not (a/is-admin? authn-info))
        user-id       (auth/current-user-id request)
        session-id    (auth/current-session-id request)
        resource-type (or (some-> resource :resource-type)
                          (some-> resource :href u/id->resource-type))
        {:keys [category default-severity]} (config/get-event-config resource-type event-type)
        new-body      (cond-> (assoc body
                                :active-claim (auth/current-active-claim request)
                                :category category
                                :severity (or severity default-severity "medium"))
                              resource-type (assoc-in [:resource :resource-type] resource-type)
                              user-id (assoc :user-id user-id)
                              session-id (assoc :session-id session-id)
                              (nil? timestamp) (assoc :timestamp (time/now-str))
                              ;; simple-user? (assoc :category "user")
                              )]
    (-> request
        (assoc :body new-body)
        ;; on a resource-deleted event, resource not present anymore
        #_(can-view-resource?))))


(def ^:dynamic *parent-event* nil)


(defmacro with-parent-event
  "Sets the given event as the parent event on any additional event created in scope"
  [event-id & body]
  `(binding [*parent-event* ~event-id]
     ~@body))


;;
;; CRUD
;;

(def add-impl (std-crud/add-fn event-resource-type collection-acl event-resource-type))


(defn- -add
  [_this {:keys [body] :as request}]
  (when (config/events-enabled (-> body :resource :resource-type))
    (let [resp (-> request
                   (prepare-event)
                   add-impl)]
      (ka-crud/publish-on-add event-resource-type resp)
      resp)))


(def retrieve-impl (std-crud/retrieve-fn event-resource-type))


(defn -retrieve [_this request]
  (retrieve-impl request))


(defn -retrieve-by-id [_this resource-id request]
  (some-> resource-id
          (db/retrieve (or request {}))
          (a/throw-cannot-view request)))


(defn -edit [_this request]
  ;; events are immutable
  (throw (r/ex-bad-method request)))


(defn -delete [_this request]
  ;; events are immutable
  (throw (r/ex-bad-method request)))


(defn -do-action [_this request]
  ;; no actions on events
  (throw (r/ex-bad-method request)))


(def query-impl (std-crud/query-fn event-resource-type collection-acl event-collection-type))


(defn -query [_this {{:keys [orderby]} :cimi-params :as request}]
  (query-impl
    (assoc-in request [:cimi-params :orderby] (if (seq orderby) orderby [["timestamp" :desc]]))))


;;
;; Utility functions
;;

(defn add-event
  [this request resource-type resource-uuid {:keys [acl severity parent] :as event}]
  (when (config/events-enabled (-> event :resource :resource-type))
    (let [active-claim   (auth/current-active-claim request)
          acl            (or acl (cond-> {:owners ["group/nuvla-admin"]}
                                         active-claim (assoc :view-data [active-claim]
                                                             :view-meta [active-claim])))
          parent         (or parent *parent-event*)
          event-map      (-> event
                             (merge {:resource-type event-resource-type
                                     :resource      (cond-> {:resource-type resource-type}
                                                            resource-uuid (assoc :href (u/resource-id resource-type resource-uuid)))
                                     :severity      severity})
                             (cond->
                               acl (assoc :acl acl)
                               parent (assoc :parent parent)))
          create-request {:params      {:resource-name event-resource-type}
                          :body        event-map
                          :nuvla/authn (auth/current-authentication request)}]
      (p/add this create-request))))


(defn -add-collection-event
  [this request resource-type event]
  (add-event this request resource-type nil event))


(defn -add-resource-event
  [this request resource-id event]
  (add-event this request (u/id->resource-type resource-id) (u/id->uuid resource-id) event))


(defn -search [_this {:keys [event-type resource-type resource-href category start end] :as _opts}]
  (some-> event-resource-type
          (crud/query-as-admin
            {:cimi-params
             {:filter (parser/parse-cimi-filter
                        (str/join " and "
                                  (cond-> []
                                          resource-type (conj (str "resource/resource-type='" resource-type "'"))
                                          resource-href (conj (str "resource/href='" resource-href "'"))
                                          event-type (conj (str "event-type='" event-type "'"))
                                          category (conj (str "category='" category "'"))
                                          start (conj (str "timestamp>='" start "'"))
                                          end (conj (str "timestamp<'" end "'")))))}})
          second))


;; CRUD wrapping

(defn -wrap-crud-add [this add-fn]
  (fn [{{resource-type :resource-name} :params :as request}]
    (let [operation "create"]
      (with-parent-event (-> (-add-collection-event
                               this request resource-type
                               {:event-type (std-events/operation-requested-event-type resource-type operation)})
                             (get-in [:body :resource-id]))
        (try
          (let [{status :status {resource-id :resource-id} :body :as response} (add-fn request)]
            (if (= 201 status)
              (-add-resource-event this request resource-id
                                   {:event-type (std-events/operation-completed-event-type resource-type operation)})
              (-add-collection-event this request resource-type
                                     {:event-type (std-events/operation-failed-event-type resource-type operation)
                                      :message    (str "Resource creation failed with status " status)}))
            response)
          (catch Throwable t
            (-add-collection-event this request resource-type
                                   {:event-type (std-events/operation-failed-event-type resource-type operation)
                                    :message    (str "Resource creation failed with an unexpected error")})
            (throw t)))))))


(defn with-resource-operation-events
  "Creates a `<resource-type>.<operation>` event, sets it as parent of subsequent events, and finally creates
   either a `<resource-type>.<operation>.completed` event or a `<resource-type>.<operation>.failed` event."
  [this resource-type resource-uuid operation request op-fn]
  (let [resource-id (u/resource-id resource-type resource-uuid)]
    (with-parent-event (-> (-add-resource-event this request resource-id
                                                {:event-type (std-events/operation-requested-event-type resource-type operation)})
                           (get-in [:body :resource-id]))
      (try
        (let [{status :status :as response} (op-fn request)]
          (if (<= 200 status 299)
            (-add-resource-event this request resource-id
                                 {:event-type (std-events/operation-completed-event-type resource-type operation)})
            (-add-resource-event this request resource-id
                                 {:event-type (std-events/operation-failed-event-type resource-type operation)
                                  :message    (str operation " failed with status " status)}))
          response)
        (catch Throwable t
          (-add-resource-event this request resource-id
                               {:event-type (std-events/operation-failed-event-type resource-type operation)
                                :message    (str operation " failed with an unexpected error")})
          (throw t))))))

(defn -wrap-crud-edit [this edit-fn]
  (fn [request]
    (let [{{resource-type :resource-name uuid :uuid} :params} request]
      (with-resource-operation-events this resource-type uuid "update" request edit-fn))))


(defn -wrap-crud-delete [this delete-fn]
  (fn [request]
    (let [{{resource-type :resource-name uuid :uuid} :params} request]
      (with-resource-operation-events this resource-type uuid "delete" request delete-fn))))


(defn -wrap-action [this action-fn]
  (fn [request]
    (let [{{resource-type :resource-name uuid :uuid action :action} :params} request]
      (with-resource-operation-events this resource-type uuid action request action-fn))))


(deftype DbEventManager []
  EventManager

  (add [this request]
    (-add this request))
  (retrieve [this request]
    (-retrieve this request))
  (retrieve-by-id [this resource-id request]
    (-retrieve-by-id this resource-id request))
  (edit [this request]
    (-edit this request))
  (delete [this request]
    (-delete this request))
  (do-action [this request]
    (-do-action this request))
  (query [this request]
    (-query this request))

  (add-collection-event [this request resource-type event]
    (-add-collection-event this request resource-type event))
  (add-resource-event [this request resource-id event]
    (-add-resource-event this request resource-id event))
  (search [this opts]
    (-search this opts))

  (wrap-crud-add [this add-fn]
    (-wrap-crud-add this add-fn))
  (wrap-crud-edit [this edit-fn]
    (-wrap-crud-edit this edit-fn))
  (wrap-crud-delete [this delete-fn]
    (-wrap-crud-delete this delete-fn))
  (wrap-action [this action-fn]
    (-wrap-action this action-fn)))
