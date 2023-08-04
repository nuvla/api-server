(ns sixsq.nuvla.server.resources.common.eventing
  "Eventing functions for resources."
  (:require [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.util.log :as logu]
            [sixsq.nuvla.server.util.time :as time]))


;; not referencing event ns to avoid circular dependencies
(def ^:const event-resource-type "event")

;; event types
;; TODO: maybe make event-type its own resource type ?

(def ^:const event-types
  {"resource.create"                       {:description      "Occurs whenever a request is issued to create a resource."
                                            :category         "command"
                                            :default-severity "medium"}
   "resource.created"                      {:description "Occurs whenever a resource is created."
                                            :category    "crud"}
   "resource.update"                       {:description "Occurs whenever a request is issued to update a resource."
                                            :category    "command"}
   "resource.updated"                      {:description "Occurs whenever a resource is updated."
                                            :category    "crud"}
   "resource.delete"                       {:description "Occurs whenever a request is issued to delete a resource."
                                            :category    "command"}
   "resource.deleted"                      {:description "Occurs whenever a resource is deleted."
                                            :category    "crud"}
   "infrastructure-service.stop.requested" {:description      "Occurs whenever a request is issued to stop an infrastructure service."
                                            :category         "command"
                                            :default-severity "low"}
   "infrastructure-service.stop.completed" {:description      "Occurs whenever a request to stop an infrastructure service completes without failures."
                                            :category         "action"
                                            :sub-category     "action-completed"
                                            :default-severity "low"}
   "infrastructure-service.stop.failed"    {:description      "Occurs whenever a request to stop an infrastructure service fails."
                                            :category         "action"
                                            :sub-category     "action-failed"
                                            :default-severity "low"}
   "infrastructure-service.state.changed"  {:description      "Occurs whenever the state of an infrastructure service changes."
                                            :category         "state"
                                            :default-severity "low"}
   "deployment.start"                      {:description    "Occurs whenever a request is issued to create a resource."
                                            :category       "command"
                                            :resource-types ["deployment"]}
   "deployment.started"                    {:description "Occurs whenever a resource is created."
                                            :category    "action"}})

;; multi-methods

(defn resource-type-dispatch [resource-type]
  resource-type)


(defn op-dispatch [operation-or-action-id]
  operation-or-action-id)


(defmulti events-enabled
          "Returns true if events should be logged for the given resource-type, false otherwise."
          resource-type-dispatch)


(defmethod events-enabled :default [_]
  true)


(defmulti operation-default-events-configuration
          "Returns the default configuration of events for the given operation.
           The configuration can be overridden by specific resources by redefining the `resource-events-configuration` multimethod.
           The configuration must be of the form:
                  {:active-when        (fn [options]) -> boolean
                   :enabled            _
                   :response-status-filter _
                   :default-event-type _
                   :default-category   _
                   :default-severity   _
                   :resource-href-prov (:request-param-uuid | :response-id | :response-resource-id)}}
                   :message-prov       (:response-message)}
          or a sequence of the above.
          Configs will be evaluated in order until a call to :active-when with the options passed to the event logging fn returns true.
          The absence of the `:active-when` means that the configurtion is always active.
          If a sequence of configs is returned, the first active configuration will be the one applied."
          op-dispatch)


(defmethod operation-default-events-configuration :default [_]
  {:enabled          true
   :default-severity "medium"})


(defn response-status-filter [accepted-status-coll]
  (fn [{:keys [response]}]
    ((set accepted-status-coll) (:status response))))


(defmethod operation-default-events-configuration :crud/add [_]
  [{:active-when        (response-status-filter #{201})
    :enabled            true
    :default-event-type "resource.created"
    :default-category   "crud"
    :default-severity   "medium"
    :resource-href-prov :response-resource-id
    :message-prov       :response-message
    :log-request-body   true
    :log-response-body  true}
   {:enabled false}])


(defmethod operation-default-events-configuration :crud/edit [_]
  [{:active-when        (response-status-filter #{200})
    :enabled            true
    :default-event-type "resource.updated"
    :default-category   "crud"
    :default-severity   "medium"
    :resource-href-prov :response-id
    :message-prov       :response-message
    :log-request-body   true
    :log-response-body  true}
   {:enabled false}])


(defmethod operation-default-events-configuration :crud/delete [_]
  [{:active-when        (response-status-filter #{200})
    :enabled            true
    :default-event-type "resource.deleted"
    :default-category   "crud"
    :default-severity   "medium"
    :resource-href-prov :response-resource-id}
   {:enabled false}])


(defmulti resource-events-configuration
          "Returns the configuration of events for the given resource-type and operation.
           The configuration must be a map keyed by the operation/action id and the map values must be of the same
           form requested by `operation-default-events-configuration` (either a map or a sequence of maps)."
          resource-type-dispatch)


(defmethod resource-events-configuration :default [_]
  {})

;; helper macros

(def ^:dynamic *parent-event* nil)


(defmacro with-parent-event
  "Sets the given event as the parent event on any additional event created in scope"
  [event-id & body]
  (when-not event-id
    (throw (ex-info "No parent event id provided" {})))
  `(binding [*parent-event* ~event-id]
     ~@body))

;; helper fns

(defn- compute-resource-href
  [{:keys [resource-href-prov]} _op {:keys [resource-href request response]}]
  (or resource-href
      (when resource-href-prov
        (case resource-href-prov
          :request-param-uuid (-> request :params :uuid)
          :response-id (-> response :body :id)
          :response-resource-id (-> response :body :resource-id)))))


(defn- compute-event-type
  [{:keys [default-event-type]} op _opts]
  (or default-event-type
      ;; use the op name as event type but remove heading colon if present
      (some->> op str (re-matches #":?(.*)") second)))


(defn- compute-message
  [{:keys [message-prov]} _op {:keys [response]}]
  (when message-prov
    (case message-prov
      :response-message (-> response :body :message))))


(defn- compute-payload
  [{:keys [log-request-body log-response-body]} _op {:keys [request response]}]
  (merge
    (when log-request-body {:request-body (:body request)})
    (when log-response-body {:response-body (:body response)})))


(defn prepare-event
  "Enriches the given event with additional info. To be called before crud/add"
  [{{:keys [event-type resource timestamp severity] :as body} :body :as request}]
  (let [;; authn-info   (auth/current-authentication request)
        ;; simple-user? (not (a/is-admin? authn-info))
        session-id    (auth/current-session-id request)
        {:keys [category default-severity]} (get event-types event-type)
        resource-type (some-> resource :href u/id->resource-type)
        new-body      (cond-> (assoc body
                                :active-claim (auth/current-active-claim request)
                                :user-id (auth/current-user-id request)
                                :category category
                                :severity (or severity default-severity "medium"))
                              resource-type (assoc-in [:resource :resource-type] resource-type)
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
  (let [{:keys [resource-types]} (get event-types event-type)
        resource-type (:resource-type resource)]
    (when (and (seq resource-types) (not ((set resource-types) resource-type)))
      (logu/log-and-throw-400
        (str "event type " event-type " not supported for resource type " resource-type)))
    event))


(defn create-event*
  "Creates an event from raw data (no resource event config involved)"
  [request resource-href {:keys [acl severity parent] :as event}]
  (let [acl            (or acl {:owners    ["group/nuvla-admin"]
                                ;; TODO: put the same acl as the resource ?
                                :view-data [(auth/current-active-claim request)]
                                :view-meta [(auth/current-active-claim request)]})
        parent         (or parent *parent-event*)
        event-map      (-> event
                           (merge {:resource-type event-resource-type
                                   :resource      {:href resource-href}
                                   :severity      severity})
                           (cond->
                             acl (assoc :acl acl)
                             parent (assoc :parent parent)))
        create-request {:params      {:resource-name event-resource-type}
                        :body        event-map
                        :nuvla/authn (auth/current-authentication request)}]
    (crud/add create-request)))


(defn- create-event1
  "Generates and store an event for the given resource and event configuration, when eventing is enabled."
  [_resource-type op event-config {:keys [request response] :as opts}]
  (let [{:keys [response-status-filter default-category default-severity]} event-config]
    (if (or (empty? response-status-filter) (response-status-filter (:status response)))
      (let [message (compute-message event-config op opts)
            payload (compute-payload event-config op opts)]
        (create-event*
          request
          (compute-resource-href event-config op opts)
          (-> {:event-type (compute-event-type event-config op opts)
               :category   default-category
               :severity   default-severity}
              (cond-> message (assoc :message message)
                      payload (assoc :payload payload))
              (merge opts)
              (dissoc :request :response)))))))


(defn- get-active-config
  "Returns the active event configuration, given the resource-type, the operation, and
   the options passed to the event logging function.
   Configurations are evaluated in sequence: the active configuration will be the first one
   for which the :active-when fn returns `true`, or where the :active-when fn is not defined."
  [m-or-ms opts]
  (->> (if (map? m-or-ms) [m-or-ms] m-or-ms)
       (filter (fn [{:keys [active-when]}]
                 (if active-when (active-when opts) true)))
       first))


(defn events-configuration
  "Returns the event configuration for the given resource-type and operation,
   by merging the active default events configuration for the operation with the
   active events configuration for the specific operation at resource-type level, if defined."
  [resource-type op opts]
  (merge
    (-> (operation-default-events-configuration op)
        (get-active-config opts))
    (-> (resource-events-configuration resource-type)
        (get op)
        (get-active-config opts))))


(defn create-event
  "Generates and store an event for the given resource and operation, when eventing is enabled.
   Retries with exponential backoff in case of failures."
  [resource-type op opts]
  (when (events-enabled resource-type)
    (let [op-event-config (events-configuration resource-type op opts)]
      (when-not (false? (:enabled op-event-config))
        ;; TODO: add exponential backoff and SEVERE log if still fails
        (let [{:keys [status body]} (create-event1 resource-type op op-event-config opts)]
          (when (= 201 status)
            (:resource-id body)))))))


(defn create-action-event
  [resource-href action opts]
  (let [op (keyword (str (u/id->resource-type resource-href) "/" action))]
    (create-event resource-href op (assoc opts :resource-href resource-href
                                               :category "action"))))


(defmacro with-action-events
  "Creates a `<resource-type>.<action>.requested` event, sets it as parent of subsequent events, and finally creates
   either a `<resource-type>.<action>.completed` event or a `<resource-type>.<action>.failed` event."
  [request & body]
  (when-not request
    (throw (ex-info "No request provided" {})))
  `(let [{{resource-type# :resource-name uuid# :uuid action# :action} :params} ~request
         resource-id#                 (u/resource-id resource-type# uuid#)
         action-requested-event-type# (str resource-type# "." action# ".requested")
         action-completed-event-type# (str resource-type# "." action# ".completed")
         action-failed-event-type#    (str resource-type# "." action# ".failed")]
     (with-parent-event (-> (create-event* ~request resource-id# {:event-type action-requested-event-type#})
                            (get-in [:body :resource-id]))
       (try
         (let [ret# (do ~@body)]
           (create-event* ~request resource-id# {:event-type action-completed-event-type#})
           ret#)
         (catch Throwable t#
           (create-event* ~request resource-id# {:event-type action-failed-event-type#})
           (throw t#))))))

