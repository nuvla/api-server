(ns com.sixsq.nuvla.server.resources.mec.app-instance
  "MEC 010-2 Application Instance Management (MEO Level)
   
   This namespace implements ETSI GS MEC 010-2 Application Lifecycle Management
   APIs at the MEO (MEC Orchestrator) level. It provides a facade over Nuvla's
   existing deployment resources to expose MEC-compliant endpoints.
   
   Scope: MEO-level orchestration only
   Standard: ETSI GS MEC 010-2 v2.2.1"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.deployment :as deployment]
    [com.sixsq.nuvla.server.resources.module :as module]
    [com.sixsq.nuvla.server.resources.nuvlabox :as nuvlabox]
    [com.sixsq.nuvla.server.resources.spec.deployment :as deployment-spec]
    [com.sixsq.nuvla.server.util.time :as time-utils]))


;;
;; State Mapping: Nuvla <-> MEC 010-2
;;

(def nuvla-to-mec-instantiation-state
  "Maps Nuvla deployment states to MEC instantiation states"
  {:CREATED            :NOT_INSTANTIATED
   :STARTING           :INSTANTIATED
   :STARTED            :INSTANTIATED
   :STOPPING           :INSTANTIATED
   :STOPPED            :INSTANTIATED
   :ERROR              :INSTANTIATED
   :PENDING            :NOT_INSTANTIATED
   :UNKNOWN            :NOT_INSTANTIATED})


(def nuvla-to-mec-operational-state
  "Maps Nuvla deployment states to MEC operational states"
  {:STARTED  :STARTED
   :STOPPED  :STOPPED
   :STARTING nil
   :STOPPING nil
   :ERROR    nil
   :CREATED  nil
   :PENDING  nil
   :UNKNOWN  nil})


(def mec-to-nuvla-state
  "Reverse mapping for state translation"
  {:NOT_INSTANTIATED :CREATED
   :INSTANTIATED      :STARTED})


;;
;; Schema Definitions (MEC 010-2)
;;

;; Note: Using simple predicates instead of specs for flexibility
;; MEC 010-2 data validation is done at the translation layer

(defn valid-app-instance-id? [id]
  (and (string? id) (str/starts-with? id "deployment/")))

(defn valid-app-d-id? [id]
  (and (string? id) (str/starts-with? id "module/")))

(defn valid-instantiation-state? [state]
  (contains? #{:NOT_INSTANTIATED :INSTANTIATED} (keyword state)))

(defn valid-operational-state? [state]
  (contains? #{:STARTED :STOPPED} (keyword state)))


;;
;; Translation Functions
;;

(defn deployment->app-instance-info
  "Translates a Nuvla deployment resource to MEC AppInstanceInfo"
  [deployment]
  (let [deployment-id   (:id deployment)
        module-ref      (:module deployment)
        module-id       (if (map? module-ref) (:href module-ref) module-ref)
        state           (keyword (:state deployment))
        host-id         (or (:nuvlabox deployment) (:parent deployment))
        instantiation   (get nuvla-to-mec-instantiation-state state :NOT_INSTANTIATED)
        operational     (get nuvla-to-mec-operational-state state)]
    (cond-> {:id                 deployment-id
             :appInstanceId      deployment-id
             :appDId             module-id
             :instantiationState (name instantiation)}
      
      ;; Add appName from module if available
      (or (:module/content deployment)
          (get-in deployment [:module :content]))
      (assoc :appName (or (get-in deployment [:module/content :name])
                          (get-in deployment [:module :content :name])
                          (get-in deployment [:module :name])))

      ;; Add appDescription from module if available
      (or (get-in deployment [:module/content :description])
          (get-in deployment [:module :content :description])
          (get-in deployment [:module :description]))
      (assoc :appDescription (or (get-in deployment [:module/content :description])
                                 (get-in deployment [:module :content :description])
                                 (get-in deployment [:module :description])))
      
      ;; Add appProvider if available
      (or (:module/author deployment)
          (get-in deployment [:module :author]))
      (assoc :appProvider (or (:module/author deployment)
                              (get-in deployment [:module :author])))
      
      ;; Add operational state if applicable
      operational
      (assoc :operationalState (name operational))
      
      ;; Add MEC host information if deployed
      host-id
      (assoc :mecHostInformation {:hostId   host-id
                                  :hostName (or (:nuvlabox-name deployment) host-id)})
      
      ;; Add HATEOAS links
      true
      (assoc :_links {:self        {:href (str "/app_lcm/v2/app_instances/" deployment-id)}
                      :instantiate {:href (str "/app_lcm/v2/app_instances/" deployment-id "/instantiate")}
                      :terminate   {:href (str "/app_lcm/v2/app_instances/" deployment-id "/terminate")}
                      :operate     {:href (str "/app_lcm/v2/app_instances/" deployment-id "/operate")}}))))


(defn app-instance-info->deployment
  "Translates MEC AppInstanceInfo to Nuvla deployment resource (partial)"
  [app-instance-info]
  (let [instantiation-state (keyword (:instantiationState app-instance-info))
        nuvla-state         (get mec-to-nuvla-state instantiation-state :CREATED)]
    {:id        (:appInstanceId app-instance-info)
     :module    (:appDId app-instance-info)
     :state     (name nuvla-state)
     :parent    (get-in app-instance-info [:mecHostInformation :hostId])}))


(defn validate-create-app-instance-request
  "Validates the CreateAppInstanceRequest payload used by POST /app_instances."
  [create-request]
  (when-not (:appDId create-request)
    (throw (ex-info "appDId is required" {:field :appDId})))
  (when-not (valid-app-d-id? (:appDId create-request))
    (throw (ex-info "Invalid appDId" {:field :appDId
                                      :value (:appDId create-request)})))
  create-request)


(defn create-request->deployment
  "Translates a CreateAppInstanceRequest to a Nuvla deployment create body."
  [create-request]
  (cond-> {:module {:href (:appDId create-request)}}
    (get-in create-request [:mecHostInformation :hostId])
    (assoc :parent (get-in create-request [:mecHostInformation :hostId]))))


;;
;; Query Functions (placeholders for integration with Nuvla CRUD)
;; These will be implemented when integrated with the actual deployment resource
;;

(comment
  "Integration points with Nuvla deployment resource:
   - get-app-instance: Call deployment CRUD read
   - list-app-instances: Call deployment CRUD query
   - create-app-instance: Call deployment CRUD create
   - delete-app-instance: Call deployment CRUD delete")


;;
;; Validation Functions
;;

(defn validate-app-instance-info
  "Validates AppInstanceInfo against MEC 010-2 requirements"
  [app-instance-info]
  (when-not (:appInstanceId app-instance-info)
    (throw (ex-info "appInstanceId is required" {:app-instance-info app-instance-info})))
  (when-not (:appDId app-instance-info)
    (throw (ex-info "appDId is required" {:app-instance-info app-instance-info})))
  (when-not (:instantiationState app-instance-info)
    (throw (ex-info "instantiationState is required" {:app-instance-info app-instance-info})))
  (when-not (valid-instantiation-state? (:instantiationState app-instance-info))
    (throw (ex-info "Invalid instantiationState" {:state (:instantiationState app-instance-info)})))
  app-instance-info)


;;
;; Lifecycle Hooks
;;

(defn on-app-instance-created
  "Hook called when an app instance is created"
  [app-instance-info]
  (log/info "MEC app instance created:" (:appInstanceId app-instance-info))
  app-instance-info)


(defn on-app-instance-deleted
  "Hook called when an app instance is deleted"
  [app-instance-id]
  (log/info "MEC app instance deleted:" app-instance-id))
