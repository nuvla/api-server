(ns sixsq.nuvla.events.util.event-manager-utils
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [sixsq.nuvla.auth.acl-resource :as acl-resource]
            [sixsq.nuvla.db.filter.parser :as parser]
            [sixsq.nuvla.events.protocol :as p]
            [sixsq.nuvla.events.std-events :as std-events]
            [sixsq.nuvla.server.util.response :as r]
            [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.events.config :as config]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.resources.event :as event]
            [sixsq.nuvla.server.util.time :as time]))

(def ^:dynamic *parent-event* nil)


(defmacro with-parent-event
  "Sets the given event as the parent event on any additional event created in scope."
  [event-id & body]
  `(binding [*parent-event* ~event-id]
     ~@body))


(defn enrich-event
  "Enriches the given event with additional info."
  [{{:keys [event-type resource timestamp severity parent] :as body} :body :as request}]
  (let [user-id       (auth/current-user-id request)
        session-id    (auth/current-session-id request)
        parent        (or parent *parent-event*)
        resource-type (or (some-> resource :resource-type)
                          (some-> resource :href u/id->resource-type))
        {:keys [category default-severity]} (config/get-event-config resource-type event-type)
        event         (cond-> (assoc body
                                :resource-type event/resource-type
                                :severity severity
                                :active-claim (auth/current-active-claim request)
                                :category category
                                :severity (or severity default-severity "medium"))
                              resource-type (assoc-in [:resource :resource-type] resource-type)
                              parent (assoc :parent parent)
                              user-id (assoc :user-id user-id)
                              session-id (assoc :session-id session-id)
                              (nil? timestamp) (assoc :timestamp (time/now-str)))]
    (assoc request :body event)))


(defn set-admin-acl
  "Sets the event acl to admin only."
  [request]
  (assoc-in request [:body :acl]
            (acl-resource/default-acl {:active-claim "group/nuvla-admin"})))


(defn set-default-acl
  "Sets the event acl as the default acl for the current user.
   For anonymous users sets the acl as the default acl of `group/nuvla-admin`"
  [request]
  (assoc-in request [:body :acl]
            (or (acl-resource/default-acl (auth/current-authentication request))
                (acl-resource/default-acl {:active-claim "group/nuvla-admin"}))))

(defn set-acl
  "Sets the event acl, taking it from the linked resource, if present,
   or from the current auth claims in the request. Returns the request."
  [{{:keys [event-type] {:keys [href resource-type]} :resource} :body :as request}]
  (if href
    (try (if-let [{:keys [acl] :as resource} (crud/retrieve-by-id-as-admin href)]
           ;; if the user has ::view-data access to the resource..
           (if (acl-resource/can-view-data? resource request)
             ;; ..set the event acl equal to that of the resource
             (assoc-in request [:body :acl] acl)
             ;; ..otherwise throw a permission error..
             (let [{:keys [allow-anon]} (config/get-event-config resource-type event-type)]
               (if allow-anon
                 ;; ..unless the event config has the `allow-anon` flag
                 (set-default-acl request)
                 ;; still allow event creation, but set the owner to group/nuvla-admin
                 (set-admin-acl request) #_(throw (r/ex-unauthorized href)))))
           (set-default-acl request))
         (catch Exception ex
           ;; if the resource does not exist..
           (if (= 404 (:status (ex-data ex)))
             ;; ..allow the creation of the event, but derive the acl from the current request
             (set-default-acl request)
             ;; bubble up any other exception
             (throw ex))))
    ;; if the event does not reference a specific resource, derive the acl from the current request
    (set-default-acl request)))


(defn validate-parent
  "Checks whether the parent property, if present, points to a valid event.
   Returns the request or throws an error when the check fails."
  [{{:keys [parent] :as event} :body :as request}]
  (when (contains? event :parent)
    (when-not (= event/resource-type (u/id->resource-type parent))
      (throw (r/ex-bad-request "Parent must be an event")))
    (crud/retrieve-by-id-as-admin parent))
  request)


(defn add-event
  "Adds an event via the event manager."
  [event-manager request resource-type resource-uuid event]
  ;; TODO: add exp backoff and severe log in case of failure
  (let [resource       (cond-> {:resource-type resource-type}
                               resource-uuid (assoc :href (u/resource-id resource-type resource-uuid)))
        create-request {:params      {:resource-name event/resource-type}
                        :body        (assoc event :resource resource)
                        :nuvla/authn (auth/current-authentication request)}]
    (p/add event-manager create-request)))


(defn add-event-exp-backoff
  "Adds an event via the event manager.
   Retries with exponential backoff in case of re-triable errors.
   Logs a severe error in case of failure."
  [event-manager request resource-type resource-uuid event]
  ;; TODO: add exp backoff
  (try
    (add-event event-manager request resource-type resource-uuid event)
    (catch Exception ex
      (log/error ex "SEVERE: An error occurred storing event via event manager, dumping the event here." {:event event})
      (throw ex))))


;;
;; Event manager protocol utility functions
;;


(defn -add-collection-event
  [this request resource-type event]
  (add-event-exp-backoff this request resource-type nil event))


(defn -add-resource-event
  [this request resource-id event]
  (add-event-exp-backoff this request (u/id->resource-type resource-id) (u/id->uuid resource-id) event))


(defn -search [_this {:keys [event-type resource-type resource-href category start end old-state new-state] :as _opts}]
  (some-> event/resource-type
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
                                          end (conj (str "timestamp<'" end "'"))
                                          old-state (conj (str "details/old-state='" old-state "'"))
                                          new-state (conj (str "details/new-state='" new-state "'")))))}})
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
