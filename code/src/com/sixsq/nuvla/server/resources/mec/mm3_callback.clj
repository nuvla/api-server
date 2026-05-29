 (ns com.sixsq.nuvla.server.resources.mec.mm3-callback
   "Internal callback receiver for southbound Mm3.003 lifecycle notifications."
   (:require
     [clojure.string :as str]
     [clojure.tools.logging :as log]
    [com.sixsq.nuvla.db.filter.parser :as parser]
     [com.sixsq.nuvla.server.resources.common.crud :as crud]
     [com.sixsq.nuvla.server.resources.mec.app-instance :as app-instance]
    [com.sixsq.nuvla.server.resources.mec.app-lcm-subscription :as subscription]
     [com.sixsq.nuvla.server.resources.mec.app-lcm-op-occ :as app-lcm-op-occ]
     [com.sixsq.nuvla.server.resources.mec.notification-dispatcher :as dispatcher]
     [com.sixsq.nuvla.server.util.response :as r]
     [com.sixsq.nuvla.server.util.time :as time]))


 (def ^:private southbound-app-instance-param-name
   "mec.app-instance-id")


 (def ^:private lifecycle-callback-uri
   "/mec/internal/mm3/app_lcm/v1/notifications")


 (defn- escape-filter-value
   [value]
   (some-> value str (str/replace "'" "\\'")))


 (defn- query-first-as-admin
   [collection-id filter-expr]
  (some->> (crud/query-as-admin collection-id {:cimi-params {:last 2
                                                             :filter (parser/parse-cimi-filter filter-expr)}})
            second
            first))


(defn- query-all-as-admin
  [collection-id filter-expr]
  (some-> (crud/query-as-admin collection-id {:cimi-params {:last 10
                                                            :filter (parser/parse-cimi-filter filter-expr)}})
          second))


 (defn- normalize-notification
   [body]
  {:notification-type   (some-> (or (:notificationType body)
                                    (:notification-type body))
                                subscription/canonical-notification-type)
    :subscription-id     (or (:subscriptionId body)
                             (:subscription-id body))
    :mepm-id             (or (:mepmId body)
                             (:mepm-id body))
    :app-instance-id     (or (:appInstanceId body)
                             (:app-instance-id body))
    :operation-id        (or (:operationId body)
                             (:operation-id body))
    :operation-type      (some-> (or (:operationType body)
                                     (:operation-type body))
                                 str
                                 str/upper-case)
    :operation-state     (some-> (or (:operationState body)
                                     (:operation-state body)
                                     (:status body))
                                 str
                                 str/upper-case)
    :instantiation-state (some-> (or (:instantiationState body)
                                     (:instantiation-state body))
                                 str
                                 str/upper-case)
    :operational-state   (some-> (or (:operationalState body)
                                     (:operational-state body))
                                 str
                                 str/upper-case)
    :previous-state      (or (:previousState body)
                             (:previous-state body))
    :change-type         (or (:changeType body)
                             (:change-type body))
    :error               (:error body)
    :raw                 body})


(defn- notification-evidence
  [notification]
  (cond-> {:received          (time/now-str)
           :notification-type (:notification-type notification)
           :subscription-id   (:subscription-id notification)
           :mepm-id           (:mepm-id notification)
           :app-instance-id   (:app-instance-id notification)
           :operation-id      (:operation-id notification)
           :operation-type    (:operation-type notification)
           :operation-state   (:operation-state notification)
           :instantiation-state (:instantiation-state notification)
           :operational-state (:operational-state notification)
           :change-type       (:change-type notification)
           :previous-state    (:previous-state notification)}
    (:error notification)
    (assoc :error (:error notification))

    (:raw notification)
    (assoc :payload (:raw notification))))


 (defn- mepm-for-notification
   [{:keys [subscription-id mepm-id]}]
   (cond
     (some? mepm-id)
     (crud/retrieve-by-id-as-admin mepm-id)

     (some? subscription-id)
     (query-first-as-admin "mepm"
                           (str "mm3-subscription-id='" (escape-filter-value subscription-id) "'"))

     :else
     nil))


(defn- persist-mepm-notification!
  [mepm notification]
  (crud/edit-by-id-as-admin (:id mepm)
                            {:mm3-last-notification (notification-evidence notification)})
  (crud/retrieve-by-id-as-admin (:id mepm)))


 (defn- deployment-from-southbound-app-id
   [southbound-app-id]
  (when southbound-app-id
    (or (some->> (query-all-as-admin "deployment"
                                     (str "mec-backing-deployment-id='" (escape-filter-value southbound-app-id) "'"))
                 (remove #(= (:id %) southbound-app-id))
                 first)
        (some-> (query-first-as-admin "deployment-parameter"
                                      (str "name='" southbound-app-instance-param-name
                                           "' and value='" (escape-filter-value southbound-app-id) "'"))
                :parent
                crud/retrieve-by-id-as-admin))))


 (defn- job-for-operation-id
   [operation-id]
   (when operation-id
     (query-first-as-admin "job"
                           (str "mec-southbound-operation-id='" (escape-filter-value operation-id) "'"))))


 (defn- operation-state->job-state
   [operation-state]
   (some-> (get app-lcm-op-occ/mec-to-nuvla-job-state
                (some-> operation-state keyword))
           name))


 (defn- infer-completed-deployment-state
   [{:keys [operation-type operational-state]} job]
   (let [resolved-operation-type (or operation-type (:mec-operation-type job))]
     (case resolved-operation-type
         "INSTANTIATE" "STARTED"
         "TERMINATE"   "CREATED"
         "OPERATE"     (or operational-state
                           (some-> (or (get-in job [:mec-request-params :changeStateTo])
                                       (get-in job [:mec-request-params "changeStateTo"]))
                                   str
                                   str/upper-case))
         nil)))


(defn- notification-has-app-instance-state?
  [{:keys [instantiation-state operational-state]}]
  (or instantiation-state operational-state))


 (defn- infer-failed-deployment-state
   [{:keys [operation-type instantiation-state operational-state]} job deployment]
   (or (when (= "NOT_INSTANTIATED" instantiation-state)
         "CREATED")
       (when (and (= "INSTANTIATED" instantiation-state)
                  (#{"STARTED" "STOPPED"} operational-state))
         operational-state)
       (case (or operation-type (:mec-operation-type job))
         "INSTANTIATE" "CREATED"
         "OPERATE"     (case (some-> (or (get-in job [:mec-request-params :changeStateTo])
                                         (get-in job [:mec-request-params "changeStateTo"]))
                                     str
                                     str/upper-case)
                         "STARTED" "STOPPED"
                         "STOPPED" "STARTED"
                         nil)
         (:state deployment))))


 (defn- infer-app-instance-deployment-state
   [{:keys [instantiation-state operational-state]} deployment]
   (cond
     (= "NOT_INSTANTIATED" instantiation-state) "CREATED"
     (and (= "INSTANTIATED" instantiation-state)
          (#{"STARTED" "STOPPED"} operational-state)) operational-state
     :else (:state deployment)))


(defn- persist-deployment-notification!
  [deployment notification & [new-state]]
  (when deployment
    (crud/edit-by-id-as-admin (:id deployment)
                              (cond-> {:mec-last-notification (notification-evidence notification)}
                                (and new-state (not= new-state (:state deployment)))
                                (assoc :state new-state)))
    (crud/retrieve-by-id-as-admin (:id deployment))))


(defn- desired-deployment-state
  [notification job deployment]
  (cond
    (nil? deployment)
    nil

    (notification-has-app-instance-state? notification)
    (infer-app-instance-deployment-state notification deployment)

    (= "COMPLETED" (:operation-state notification))
    (infer-completed-deployment-state notification job)

    (= "FAILED" (:operation-state notification))
    (infer-failed-deployment-state notification job deployment)

    :else
    nil))


 (defn- app-instance-change
   [previous current]
   (cond
     (not= (:instantiationState previous) (:instantiationState current))
     ["INSTANTIATION_STATE" (:instantiationState previous)]

     (not= (:operationalState previous) (:operationalState current))
     ["OPERATIONAL_STATE" (:operationalState previous)]

     :else
     nil))


 (defn- dispatch-app-instance-change!
   [previous-deployment current-deployment]
   (let [previous-info (app-instance/deployment->app-instance-info previous-deployment)
         current-info  (app-instance/deployment->app-instance-info current-deployment)]
     (when-let [[change-type previous-state] (app-instance-change previous-info current-info)]
       (dispatcher/dispatch-app-instance-state-change! current-info change-type previous-state))))


 (defn- update-job-state!
   [job new-state notification]
   (let [old-op-occ       (app-lcm-op-occ/job->app-lcm-op-occ job)
         previous-op-state (:operationState old-op-occ)
        final-job-state?  (#{"SUCCESS" "FAILED" "STOPPED" "CANCELED"} new-state)
         edit-body        (cond-> {:state new-state
                                   :mec-last-notification (notification-evidence notification)}
                            (and (= new-state "FAILED")
                                 (or (get-in notification [:error :detail])
                                     (get-in notification [:error :message])))
                            (assoc :status-message (or (get-in notification [:error :detail])
                                                       (get-in notification [:error :message]))))]
     (crud/edit-by-id-as-admin (:id job) edit-body)
     (let [updated-job (crud/retrieve-by-id-as-admin (:id job))
           updated-op  (app-lcm-op-occ/job->app-lcm-op-occ updated-job)]
      (dispatcher/dispatch-app-lcm-op-occ-state-change! updated-op
                                                        (if final-job-state?
                                                          "OPERATION_RESULT"
                                                          "OPERATION_STATE")
                                                        previous-op-state)
       updated-job)))


 (defn- reconcile-operation-notification!
   [notification mepm]
   (let [job        (or (job-for-operation-id (:operation-id notification))
                        (throw (ex-info "Unable to correlate operation notification to a job"
                                        {:status 404
                                         :operation-id (:operation-id notification)})))
         _          (when (and (:mepm-id job) mepm (not= (:mepm-id job) (:id mepm)))
                      (throw (ex-info "Operation notification MEPM mismatch"
                                      {:status 409
                                       :job-id (:id job)
                                       :expected-mepm-id (:mepm-id job)
                                       :received-mepm-id (:id mepm)})))
         deployment (or (some-> (:target-resource job) :href crud/retrieve-by-id-as-admin)
                        (deployment-from-southbound-app-id (:app-instance-id notification)))
         operation-state (:operation-state notification)
         new-job-state (operation-state->job-state operation-state)
         deployment-state (desired-deployment-state notification job deployment)]
     (cond
       (nil? new-job-state)
       {:action "ignored" :reason "unsupported-operation-state" :job-id (:id job)}

       (= (:state job) new-job-state)
      (if-let [updated-deployment (and deployment
                                       (persist-deployment-notification! deployment notification deployment-state))]
        (do
          (dispatch-app-instance-change! deployment updated-deployment)
          {:action "updated-deployment"
           :job-id (:id job)
           :job-state new-job-state
           :deployment-id (:id updated-deployment)})
        {:action "noop" :job-id (:id job) :job-state new-job-state})

       :else
      (let [updated-deployment (and deployment
                                    (persist-deployment-notification! deployment notification deployment-state))]
        (when updated-deployment
          (dispatch-app-instance-change! deployment updated-deployment))
        (update-job-state! job new-job-state notification)
         {:action "updated"
          :job-id (:id job)
          :job-state new-job-state
          :deployment-id (:id deployment)}))))


 (defn- reconcile-app-instance-notification!
   [notification]
   (let [deployment (or (deployment-from-southbound-app-id (:app-instance-id notification))
                        (throw (ex-info "Unable to correlate app instance notification to a deployment"
                                        {:status 404
                                         :app-instance-id (:app-instance-id notification)})))
         next-state (infer-app-instance-deployment-state notification deployment)]
    (let [updated-deployment (persist-deployment-notification! deployment notification next-state)]
      (if (= next-state (:state deployment))
        {:action "recorded" :deployment-id (:id deployment) :state next-state}
        (do
          (dispatch-app-instance-change! deployment updated-deployment)
          {:action "updated"
           :deployment-id (:id deployment)
           :state next-state})))))


 (defn handle-notification
   "POST /mec/internal/mm3/app_lcm/v1/notifications - receive southbound
   lifecycle notifications from a MEPM."
   [request]
   (try
     (let [notification (normalize-notification (:body request))
           mepm         (mepm-for-notification notification)]
       (when-not mepm
         (throw (ex-info "Unable to correlate southbound notification to a MEPM"
                         {:status 404
                          :subscription-id (:subscription-id notification)
                          :mepm-id (:mepm-id notification)})))
       (persist-mepm-notification! mepm notification)
       (-> (case (:notification-type notification)
             "AppLcmOpOccNotification" (reconcile-operation-notification! notification mepm)
             "AppInstNotification" (reconcile-app-instance-notification! notification)
             (throw (ex-info "Unsupported Mm3.003 notification type"
                             {:status 400
                              :notification-type (:notification-type notification)})))
           (assoc :mepm-id (:id mepm)
                  :notification-type (:notification-type notification))
           r/json-response
           (assoc :status 202)))
     (catch clojure.lang.ExceptionInfo e
       (let [status (or (:status (ex-data e)) 400)]
         (log/warn e "Failed to process Mm3.003 notification")
         (-> {:error   (ex-message e)
              :details (ex-data e)}
             r/json-response
             (assoc :status status))))
     (catch Exception e
       (log/error e "Unexpected error processing Mm3.003 notification")
       (-> {:error "Unexpected error processing southbound notification"}
           r/json-response
           (assoc :status 500)))))
