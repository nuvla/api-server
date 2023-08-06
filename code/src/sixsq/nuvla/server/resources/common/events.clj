(ns sixsq.nuvla.server.resources.common.events
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-events :as std-events]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.time :as time]))

;; not referencing event ns to avoid circular dependencies
(def ^:const event-resource-type "event")


(def ^:dynamic *parent-event* nil)


(defn resource-type-dispatch [resource-type]
  resource-type)


(defmulti events-enabled
          "Returns true if events should be logged for the given resource-type, false otherwise."
          resource-type-dispatch)


(defn events-enabled-impl [_] true)

(defmethod events-enabled :default
  [resource-type]
  (events-enabled-impl resource-type))


(defmulti supported-event-types
          "Returns a map where the keys are the event types supported by the resource type and
           the values are the configuration of the event types."
          resource-type-dispatch)


(defn supported-event-types-impl
  [resource-type]
  (merge
    (std-events/crud-event-types resource-type)
    (std-events/actions-event-types resource-type)))


(defmethod supported-event-types :default
  [resource-type]
  (supported-event-types-impl resource-type))


(defn get-event-config
  [resource-type event-type]
  (or (-> resource-type
          (supported-event-types)
          (get event-type))
      (logu/log-and-throw-400 (str "Event `" event-type "` not supported on resource type " resource-type))))


(defmacro with-parent-event
  "Sets the given event as the parent event on any additional event created in scope"
  [event-id & body]
  (when-not event-id
    (throw (ex-info "No parent event id provided" {})))
  `(binding [*parent-event* ~event-id]
     ~@body))


(defn prepare-event
  "Enriches the given event with additional info. To be called before crud/add"
  [{{:keys [event-type resource timestamp severity] :as body} :body :as request}]
  (let [;; authn-info   (auth/current-authentication request)
        ;; simple-user? (not (a/is-admin? authn-info))
        user-id       (auth/current-user-id request)
        session-id    (auth/current-session-id request)
        resource-type (or (some-> resource :resource-type)
                          (some-> resource :href u/id->resource-type))
        {:keys [category default-severity]} (get-event-config resource-type event-type)
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


(defn validate-event
  [{:keys [event-type resource] :as event}]
  (let [resource-type (:resource-type resource)]
    (when-not (-> resource-type
                  (supported-event-types)
                  (get event-type))
      (logu/log-and-throw-400
        (str "event type " event-type " not supported for resource type " resource-type)))
    event))


(defn- create-event*
  "Creates an event from raw data (no resource event config involved)"
  [request resource-type resource-uuid {:keys [acl severity parent] :as event}]
  (when (events-enabled resource-type)
    (let [active-claim   (auth/current-active-claim request)
          acl            (or acl (cond-> {:owners    ["group/nuvla-admin"]}
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
      (crud/add create-request))))


(defn create-collection-event
  [request resource-type event]
  (create-event* request resource-type nil event))


(defn create-resource-event
  [request resource-id event]
  (create-event* request (u/id->resource-type resource-id) (u/id->uuid resource-id) event))


(defn with-resource-operation-events
  "Creates a `<resource-type>.<operation>.requested` event, sets it as parent of subsequent events, and finally creates
   either a `<resource-type>.<operation>.completed` event or a `<resource-type>.<operation>.failed` event."
  [resource-type resource-uuid operation request body-fn]
  (let [resource-id (u/resource-id resource-type resource-uuid)]
    (with-parent-event (-> (create-resource-event request resource-id
                                                  {:event-type (std-events/operation-requested-event-type resource-type operation)})
                           (get-in [:body :resource-id]))
                       (try
                         (let [response (body-fn)]
                           (create-resource-event request resource-id
                                                  {:event-type (std-events/operation-completed-event-type resource-type operation)})
                           response)
                         (catch Throwable t
                           (create-resource-event request resource-id
                                                  {:event-type (std-events/operation-failed-event-type resource-type operation)})
                           (throw t))))))


(defmacro with-action-events
  [request & body]
  (when-not request
    (throw (ex-info "No request provided" {})))
  `(let [{{resource-type# :resource-name uuid# :uuid action# :action} :params} ~request]
     (with-resource-operation-events resource-type# uuid# action# ~request (fn [] ~@body))))


(defn with-resource-create-events
  [resource-type request body-fn]
  (let [operation "create"]
    (with-parent-event (-> (create-collection-event
                             request resource-type
                             {:event-type (std-events/operation-requested-event-type resource-type operation)})
                           (get-in [:body :resource-id]))
                       (try
                         (let [{status :status {resource-id :resource-id} :body :as response} (body-fn)]
                           (if (= 201 status)
                             (create-resource-event request resource-id
                                                    {:event-type (std-events/operation-completed-event-type resource-type operation)})
                             (create-collection-event request resource-type
                                                      {:event-type (std-events/operation-failed-event-type resource-type operation)
                                                       :message    (str "Resource creation failed with status " status)}))
                           response)
                         (catch Throwable t
                           (create-collection-event request resource-type
                                                    {:event-type (std-events/operation-failed-event-type resource-type operation)
                                                     :message    (str "Resource creation failed with an unxpected error")})
                           (log/error t)
                           (throw t))))))


(defmacro with-crud-create-events
  [request & body]
  `(let [{{resource-type# :resource-name} :params} ~request]
     (with-resource-create-events resource-type# ~request (fn [] ~@body))))


(defmacro with-crud-update-events
  [request & body]
  `(let [{{resource-type# :resource-name uuid# :uuid} :params} ~request]
     (with-resource-operation-events resource-type# uuid# "update" ~request (fn [] ~@body))))


(defmacro with-crud-delete-events
  [request & body]
  `(let [{{resource-type# :resource-name uuid# :uuid} :params} ~request]
     (with-resource-operation-events resource-type# uuid# "delete" ~request (fn [] ~@body))))


