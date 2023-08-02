(ns sixsq.nuvla.server.resources.common.eventing
  "Eventing functions for resources."
  (:require [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.auth.acl-resource :as acl-resource]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.util.time :as time]))


;; not referencing event ns to avoid circular dependencies
(def ^:const event-resource-type "event")


;; Default event config
(def event-default-config
  {:enabled                true
   :default-severity       "medium"})


;; Default event config per operation
;;
;;   {<crud operation id/action id>
;;     {:enabled            _
;;      :response-status-filter _
;;      :default-event-type _
;;      :default-category   _
;;      :default-severity   _
;;      :resource-href-prov (:request-param-uuid | :response-id | :response-resource-id)}}
;;      :message-prov       (:response-message)
(def op-event-default-configs
  {:crud/add    {:enabled                true
                 :response-status-filter #{201}
                 :default-event-type     "resource-created"
                 :default-category       "crud"
                 :default-severity       "medium"
                 :resource-href-prov     :response-resource-id
                 :message-prov           :response-message
                 :log-request-body       true
                 :log-response-body      true}
   :crud/edit   {:enabled                true
                 :response-status-filter #{200}
                 :default-event-type     "resource-updated"
                 :default-category       "crud"
                 :default-severity       "medium"
                 :resource-href-prov     :response-id
                 :message-prov           :response-message
                 :log-request-body       true
                 :log-response-body      true}
   :crud/delete {:enabled                true
                 :response-status-filter #{200}
                 :default-event-type     "resource-deleted"
                 :default-category       "crud"
                 :default-severity       "medium"
                 :resource-href-prov     :response-resource-id}})


(def ^:const initial-resource-event-registry
  ;; disable eventing for the `event` resource type
  {event-resource-type {:enabled false}})


;; Dynamic event config registry, keyed by resource and operation.
;; Takes precedence over the event configs per operation above.
;;
;; {<resource type>
;;   {:enabled _
;;    :ops     <see op-event-default-configs map schema above>
(def resource-event-registry (atom initial-resource-event-registry))


(defn configure-resource-type-events
  "Register the configuration for an event linked to an operation on a resource"
  [resource-type op event-config]
  (swap! resource-event-registry assoc-in [resource-type op] event-config))


(def ^:dynamic parent-event nil)


(defmacro with-parent-event
  "Sets the given event as the parent event on any additional event created in scope"
  [event-id & body]
  (when-not event-id
    (throw (ex-info "No parent event id provided" {})))
  `(binding [parent-event ~event-id]
     ~@body))


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


(defn create-event*
  "Creates an event from raw data (no event config registry involved)"
  [resource-href {:keys [request acl active-claim session-id user-id
                         event-type severity category timestamp parent
                         message state payload]}]
  (when-not (or active-claim request)
    (throw (IllegalArgumentException. (str "either `active-claims` or `request` must be provided"))))
  (when-not (or user-id request)
    (throw (IllegalArgumentException. (str "either `user-id` or `request` must be provided"))))
  (let [session-id     (or session-id (auth/current-session-id request))
        acl            (or acl {:owners    ["group/nuvla-admin"]
                                ;; TODO: put the same acl as the resource ?
                                :view-data [(auth/current-active-claim request)]
                                :view-meta [(auth/current-active-claim request)]})
        payload        (or payload (merge (when state) {:state state}))
        parent         (or parent parent-event)
        event-map      (cond-> {:resource-type event-resource-type
                                :event-type    event-type
                                :resource      {:href          resource-href
                                                :resource-type (u/id->resource-type resource-href)}
                                :severity      severity
                                :category      category
                                :timestamp     (or timestamp (time/now-str))
                                :active-claim  (or active-claim (auth/current-active-claim request))
                                :user-id       (or user-id (auth/current-user-id request))}
                               session-id (assoc :session-id session-id)
                               acl (assoc :acl acl)
                               parent (assoc :parent parent)
                               message (assoc :message message)
                               payload (assoc :payload payload))
        create-request {:params      {:resource-name event-resource-type}
                        :body        event-map
                        :nuvla/authn auth/internal-identity}]
    (crud/add create-request)))


(defn- create-event1
  "Generates and store an event for the given resource and event configuration, when eventing is enabled."
  [_resource-type op event-config {:keys [request response] :as opts}]
  (let [{:keys [response-status-filter default-event-type default-category default-severity]} event-config]
    (if (or (empty? response-status-filter) (response-status-filter (:status response)))
      (create-event*
        (compute-resource-href event-config op opts)
        (merge
          {:event-type (compute-event-type event-config op opts)
           :category   default-category
           :severity   default-severity
           :message    (compute-message event-config op opts)
           :request    request
           :payload    (compute-payload event-config op opts)}
          opts)))))


(defn create-event
  "Generates and store an event for the given resource and operation, when eventing is enabled.
   Retries with exponential backoff in case of failures."
  [resource-type op opts]
  (let [resource-event-config (get @resource-event-registry resource-type)]
    (when-not (false? (:enabled resource-event-config))
      (let [op-event-config (merge
                              event-default-config
                              (get op-event-default-configs op)
                              (get resource-event-config op))]
        (when-not (false? (:enabled op-event-config))
          ;; TODO: add exponential backoff and SEVERE log if still fails
          (let [{:keys [status body]} (create-event1 resource-type op op-event-config opts)]
            (when (= 201 status)
              (:resource-id body))))))))


(defn create-action-event
  [resource-href action opts]
  (let [op (keyword (str (u/id->resource-type resource-href) "/" action))]
    (create-event resource-href op (assoc opts :resource-href resource-href
                                               :category "action"))))

