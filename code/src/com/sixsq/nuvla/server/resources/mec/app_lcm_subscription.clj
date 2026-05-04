(ns com.sixsq.nuvla.server.resources.mec.app-lcm-subscription
  "MEC 010-2 Application Lifecycle Subscription
   
   Implements ETSI GS MEC 010-2 v2.2.1 subscription model for notifications:
   - AppInstanceStateChangeNotification: App instance state changes
   - AppLcmOpOccStateChangeNotification: Operation occurrence state changes
   
   Subscriptions allow MEO consumers to receive asynchronous notifications
   about lifecycle events via HTTP callbacks."
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.mec-subscription :as mec-subscription]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const api-resource-type "subscription")

(def ^:const resource-type "mec-subscription")

(def ^:const collection-type "mec-subscription-collection")

(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; Subscription Types
;;

(def subscription-types
  "Valid MEC 010-2 subscription types"
  #{"AppInstanceStateChangeNotification"
    "AppLcmOpOccStateChangeNotification"})


;;
;; Notification Types (aligned with subscription types)
;;

(def notification-types
  "Valid MEC 010-2 notification types"
  #{"AppInstanceStateChangeNotification"
    "AppLcmOpOccStateChangeNotification"})


;;
;; Change Types
;;

(def app-instance-change-types
  "Change types for AppInstance notifications"
  #{"INSTANTIATION_STATE"    ; instantiationState changed
    "OPERATIONAL_STATE"      ; operationalState changed
    "CONFIGURATION"})        ; Configuration changed

(def op-occ-change-types
  "Change types for AppLcmOpOcc notifications"
  #{"OPERATION_STATE"        ; operationState changed
    "OPERATION_RESULT"})     ; Operation completed with result


;;
;; Schema Definitions
;;

(s/def ::subscription-type subscription-types)
(s/def ::callback-uri (s/and string? #(re-matches #"https?://.*" %)))

;; Filter for AppInstance notifications
(s/def ::app-instance-id (s/nilable string?))
(s/def ::app-name (s/nilable string?))
(s/def ::operational-state (s/nilable #{"STARTED" "STOPPED" "UNKNOWN"}))
(s/def ::instantiation-state (s/nilable #{"NOT_INSTANTIATED" "INSTANTIATED"}))

(s/def ::app-instance-filter
  (s/keys :opt-un [::app-instance-id
                   ::app-name
                   ::operational-state
                   ::instantiation-state]))

;; Filter for AppLcmOpOcc notifications
(s/def ::operation-type (s/nilable #{"INSTANTIATE" "TERMINATE" "OPERATE"}))
(s/def ::operation-state (s/nilable #{"STARTING" "PROCESSING" "COMPLETED" "FAILED" "ROLLED_BACK"}))

(s/def ::app-lcm-op-occ-filter
  (s/keys :opt-un [::app-instance-id
                   ::operation-type
                   ::operation-state]))

;; Subscription resource
(s/def ::subscription
  (s/keys :req-un [::common/id
                   ::subscription-type
                   ::callback-uri]
          :opt-un [::app-instance-filter
                   ::app-lcm-op-occ-filter
                   ::mec-subscription/owner
                   ::mec-subscription/active]))


;;
;; Subscription Resource Functions
;;

(defn create-subscription
  "Create a new subscription resource.
   
   Parameters:
   - subscription-type: One of subscription-types
   - callback-uri: HTTP(S) URI for webhook callbacks
   - filter-opts: Optional filter criteria (map)
   - user-id: Owner of the subscription
   
   Returns:
   Subscription resource map with generated ID"
  [subscription-type callback-uri filter-opts user-id]
  (let [subscription-id (str api-resource-type "/" (java.util.UUID/randomUUID))
        now             (str (java.time.Instant/now))
        filter-key      (case subscription-type
                          "AppInstanceStateChangeNotification"
                          :app-instance-filter
                          
                          "AppLcmOpOccStateChangeNotification"
                          :app-lcm-op-occ-filter
                          
                          nil)]
    (cond-> {:id                subscription-id
             :subscription-type subscription-type
             :callback-uri      callback-uri
             :created           now
             :updated           now
             :owner             user-id
             :active            true}
      
      (and filter-key (seq filter-opts))
      (assoc filter-key filter-opts))))


(defn validate-subscription
  "Validate subscription resource against spec.
   
   Returns:
   - {:valid? true} if valid
   - {:valid? false :errors [...]} if invalid"
  [subscription]
  (if (s/valid? ::subscription subscription)
    {:valid? true}
    {:valid? false
     :errors (s/explain-data ::subscription subscription)}))


(defn api-id->resource-id
  [subscription-id]
  (let [[_ uuid] (u/parse-id subscription-id)]
    (when uuid
      (str resource-type "/" uuid))))


(defn resource-id->api-id
  [subscription-id]
  (let [[_ uuid] (u/parse-id subscription-id)]
    (when uuid
      (str api-resource-type "/" uuid))))


(defn resource->api-subscription
  [resource]
  (when resource
    (update resource :id resource-id->api-id)))


(def resource-metadata (gen-md/generate-metadata ::ns ::mec-subscription/schema))

(defn initialize
  []
  (std-crud/initialize resource-type ::mec-subscription/schema)
  (md/register resource-metadata))

(def validate-fn (u/create-spec-validation-fn ::mec-subscription/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [request]
  (add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))

(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))

(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(def delete-impl (std-crud/delete-fn resource-type))

(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))

(defmethod crud/query resource-type
  [request]
  (query-impl request))


(defn update-subscription
  "Update subscription resource fields.
   
   Allowed updates:
   - callback-uri
   - filter (app-instance-filter or app-lcm-op-occ-filter)
   - active (boolean)
   
   Returns:
   Updated subscription map"
  [subscription updates]
  (let [now              (java.time.Instant/now)
        allowed-updates  (select-keys updates [:callback-uri
                                                :app-instance-filter
                                                :app-lcm-op-occ-filter
                                                :active])]
    (-> subscription
        (merge allowed-updates)
        (assoc :updated now))))


(defn deactivate-subscription
  "Mark subscription as inactive (soft delete).
   
   Returns:
   Updated subscription map with :active false"
  [subscription]
  (assoc subscription
         :active false
         :updated (java.time.Instant/now)))


;;
;; Filter Matching
;;

(defn- matches-filter?
  "Check if a value matches filter criteria.
   
   Filter criteria:
   - nil: matches anything (no filter)
   - value: must equal value
   - collection: must be in collection"
  [filter-value actual-value]
  (cond
    (nil? filter-value)
    true
    
    (coll? filter-value)
    (contains? (set filter-value) actual-value)
    
    :else
    (= filter-value actual-value)))


(defn matches-app-instance-filter?
  "Check if app instance matches subscription filter.
   
   Parameters:
   - subscription: Subscription resource
   - app-instance: AppInstance resource
   
   Returns:
   Boolean indicating if app instance matches filter"
  [subscription app-instance]
  (let [filter (:app-instance-filter subscription)]
    (if (empty? filter)
      true ; No filter = match all
      (and
        (matches-filter? (:app-instance-id filter) (:id app-instance))
        (matches-filter? (:app-name filter) (:app-name app-instance))
        (matches-filter? (:operational-state filter) (:operational-state app-instance))
        (matches-filter? (:instantiation-state filter) (:instantiation-state app-instance))))))


(defn matches-app-lcm-op-occ-filter?
  "Check if operation occurrence matches subscription filter.
   
   Parameters:
   - subscription: Subscription resource
   - app-lcm-op-occ: AppLcmOpOcc resource
   
   Returns:
   Boolean indicating if operation matches filter"
  [subscription app-lcm-op-occ]
  (let [filter (:app-lcm-op-occ-filter subscription)]
    (if (empty? filter)
      true ; No filter = match all
      (and
        (matches-filter? (:app-instance-id filter) (:app-instance-id app-lcm-op-occ))
        (matches-filter? (:operation-type filter) (:operation-type app-lcm-op-occ))
        (matches-filter? (:operation-state filter) (:operation-state app-lcm-op-occ))))))


;;
;; Notification Building
;;

(defn build-app-instance-notification
  "Build AppInstanceStateChangeNotification.
   
   Parameters:
   - subscription: Subscription resource
   - app-instance: AppInstance resource
   - change-type: Type of change (from app-instance-change-types)
   - previous-state: Previous state before change (optional)
   
   Returns:
   Notification map ready for delivery"
  [subscription app-instance change-type previous-state]
  {:notification-type    "AppInstanceStateChangeNotification"
   :notification-id      (str "notification/" (java.util.UUID/randomUUID))
   :subscription-id      (:id subscription)
   :timestamp            (java.time.Instant/now)
   :app-instance-id      (:id app-instance)
   :app-name             (:app-name app-instance)
   :app-d-id             (:app-d-id app-instance)
   :instantiation-state  (:instantiation-state app-instance)
   :operational-state    (:operational-state app-instance)
   :change-type          change-type
   :previous-state       previous-state
   :_links               {:subscription {:href (str "/mec/app_lcm/v2/subscriptions/" (:id subscription))}
                          :app-instance {:href (str "/mec/app_lcm/v2/app_instances/" (:id app-instance))}}})


(defn build-app-lcm-op-occ-notification
  "Build AppLcmOpOccStateChangeNotification.
   
   Parameters:
   - subscription: Subscription resource
   - app-lcm-op-occ: AppLcmOpOcc resource
   - change-type: Type of change (from op-occ-change-types)
   - previous-state: Previous state before change (optional)
   
   Returns:
   Notification map ready for delivery"
  [subscription app-lcm-op-occ change-type previous-state]
  {:notification-type       "AppLcmOpOccStateChangeNotification"
   :notification-id         (str "notification/" (java.util.UUID/randomUUID))
   :subscription-id         (:id subscription)
   :timestamp               (java.time.Instant/now)
   :app-lcm-op-occ-id       (:id app-lcm-op-occ)
   :app-instance-id         (:app-instance-id app-lcm-op-occ)
   :operation-type          (:operation-type app-lcm-op-occ)
   :operation-state         (:operation-state app-lcm-op-occ)
   :change-type             change-type
   :previous-state          previous-state
   :start-time              (:start-time app-lcm-op-occ)
   :state-entered-time      (:state-entered-time app-lcm-op-occ)
   :_links                  {:subscription {:href (str "/mec/app_lcm/v2/subscriptions/" (:id subscription))}
                             :app-lcm-op-occ {:href (str "/mec/app_lcm/v2/app_lcm_op_occs/" (:id app-lcm-op-occ))}
                             :app-instance {:href (str "/mec/app_lcm/v2/app_instances/" (:app-instance-id app-lcm-op-occ))}}})


;;
;; Query Functions
;;

(defn query-subscriptions
  "Query subscriptions with optional filters.
   
   Parameters:
   - subscriptions: Collection of subscription resources
   - opts: Query options
     * :subscription-type - Filter by subscription type
     * :owner - Filter by owner
     * :active - Filter by active status (default true)
     * :limit - Maximum results (default 100)
     * :offset - Skip first N results (default 0)
   
   Returns:
   Filtered and paginated collection of subscriptions"
  [subscriptions {:keys [subscription-type owner active limit offset]
                  :or   {active true limit 100 offset 0}}]
  (->> subscriptions
       (filter (fn [sub]
                 (and
                   (or (nil? subscription-type)
                       (= (:subscription-type sub) subscription-type))
                   (or (nil? owner)
                       (= (:owner sub) owner))
                   (or (nil? active)
                       (= (:active sub) active)))))
       (drop offset)
       (take limit)))


(defn get-subscription-by-id
  "Get subscription by ID.
   
   Parameters:
   - subscriptions: Collection of subscription resources
   - subscription-id: Subscription ID
   
   Returns:
   Subscription resource or nil if not found"
  [subscriptions subscription-id]
  (first (filter #(= (:id %) subscription-id) subscriptions)))


(defn get-active-subscriptions-for-type
  "Get all active subscriptions for a specific notification type.
   
   Parameters:
   - subscriptions: Collection of subscription resources
   - subscription-type: Subscription type to filter
   
   Returns:
   Collection of active subscriptions"
  [subscriptions subscription-type]
  (filter (fn [sub]
            (and (:active sub)
                 (= (:subscription-type sub) subscription-type)))
          subscriptions))
