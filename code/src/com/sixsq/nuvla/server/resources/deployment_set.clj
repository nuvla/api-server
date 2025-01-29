(ns com.sixsq.nuvla.server.resources.deployment-set
  "
These resources represent a deployment set that regroups deployments.
"
  (:require
    [clojure.data.json :as json]
    [clojure.set :as set]
    [clojure.string :as str]
    [com.sixsq.nuvla.server.resources.deployment :as deployment]
    [com.sixsq.nuvla.server.resources.deployment.utils :as dep-utils]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.state-machine :as sm]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.deployment-set.operational-status :as os]
    [com.sixsq.nuvla.server.resources.deployment-set.utils :as utils]
    [com.sixsq.nuvla.server.resources.job.interface :as job-interface]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.module :as module]
    [com.sixsq.nuvla.server.resources.module.utils :as module-utils]
    [com.sixsq.nuvla.server.resources.nuvlabox :as nuvlabox]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.deployment-set :as spec]
    [com.sixsq.nuvla.server.resources.spec.module :as module-spec]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox :as nb-spec]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]
    [com.sixsq.nuvla.server.util.response :as r]
    [com.sixsq.nuvla.server.util.time :as t]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const collection-type (u/ns->collection-type *ns*))

(def ^:const create-type (u/ns->create-type *ns*))

(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})

(def actions [{:name           utils/action-start
               :uri            utils/action-start
               :description    "start deployment set"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           utils/action-stop
               :uri            utils/action-stop
               :description    "stop deployment set"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           utils/action-cancel
               :uri            utils/action-cancel
               :description    "cancel running action on deployment set"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           utils/action-update
               :uri            utils/action-update
               :description    "cancel running action on deployment set"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           utils/action-plan
               :uri            utils/action-plan
               :description    "get an action plan for deployment set"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           utils/action-check-requirements
               :uri            utils/action-check-requirements
               :description    "check whether the edges in the deployment set satisfy the apps requirements"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}])

(defmethod sm/state-machine resource-type
  [_resource]
  utils/state-machine)

;;
;; validate deployment set
;;

(def validate-fn (u/create-spec-validation-fn ::spec/deployment-set))

(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))

;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [{dg-id :id :as resource} request]
  (let [current-user (auth/current-active-claim request)]
    (assoc resource :acl {:owners    ["group/nuvla-admin"]
                          :edit-data [dg-id current-user]
                          :view-acl  [dg-id current-user]
                          :manage    [dg-id current-user]
                          :delete    [current-user]})))

;;
;; CRUD operations
;;

(defn load-resource-throw-not-allowed-action
  [{{:keys [uuid]} :params :as request}]
  (-> (str resource-type "/" uuid)
      crud/retrieve-by-id-as-admin
      (a/throw-cannot-manage request)
      (sm/throw-can-not-do-action request)))

(defn divergence-map
  ([request]
   (divergence-map (load-resource-throw-not-allowed-action request) request))
  ([{:keys [applications-sets] :as deployment-set} _request]
   (when (seq applications-sets)
     (let [owner-request     (auth/get-owner-request deployment-set)
           applications-sets (-> deployment-set
                                 utils/get-applications-sets-href
                                 (crud/get-resource-throw-nok owner-request))
           missing-edges     (utils/get-missing-edges deployment-set owner-request)
           planned           (remove
                               #(contains? missing-edges (:target %))
                               (utils/plan deployment-set applications-sets))
           divergence        (cond->
                               (os/divergence-map
                                 planned
                                 (utils/current-state deployment-set))
                               (seq missing-edges) (assoc :missing-edges (vec missing-edges)))
           status            (if (some (comp pos? count) (vals divergence))
                               utils/operational-status-nok
                               utils/operational-status-ok)]
       (assoc divergence :status status)))))

(defn create-module
  [module]
  (let [{{:keys [status resource-id]} :body
         :as                          response} (module-utils/create-module module)]
    (if (= status 201)
      resource-id
      (log/errorf "unexpected status code (%s) when creating %s resource: %s"
                  (str status) module response))))

(defn retrieve-module-as
  [id authn]
  (let [{:keys [status body]} (crud/retrieve {:params         {:uuid          (u/id->uuid id)
                                                               :resource-name module/resource-type}
                                              :request-method :get
                                              :nuvla/authn    authn})]
    (if (and (= 200 status) body)
      body
      (throw (r/ex-response "App must be visible to DG owner" 403 id)))))

(defn create-module-apps-set
  [{:keys [owner modules] :as resource} request]
  (let [modules-data (mapv #(retrieve-module-as % (auth/get-owner-authn resource))
                           (distinct modules))]
    (create-module
      {:path    (str module-utils/project-apps-sets "/" (u/rand-uuid))
       :subtype module-spec/subtype-apps-sets
       :acl     {:owners [owner]}
       :content {:commit "no commit message"
                 :author (auth/current-active-claim request)
                 :applications-sets
                 [{:name         "Main"
                   :applications (map #(hash-map :id (module-utils/full-uuid->uuid (:id %))
                                                 :version
                                                 (module-utils/latest-or-version-index
                                                   % (:id %)))
                                      modules-data)}]}})))

(defn retrieve-edge-as
  [id authn]
  (let [{:keys [status body]} (crud/retrieve {:params         {:uuid          (u/id->uuid id)
                                                               :resource-name nuvlabox/resource-type}
                                              :request-method :get
                                              :nuvla/authn    authn})]
    (if (and (= 200 status) body)
      body
      (throw (r/ex-response "Edge must be visible to DG owner" 403 id)))))

(defn replace-modules-by-apps-set
  "Removes top level keys :modules and :overwrites, creates an app set with those modules and
   overwrites, and makes the deployment set point to the new app-set.
   A top level :fleet and/or :fleet-filter keys are also required:
   If :fleet is not specified, it is computed by querying edges satisfying the :fleet-filter.
   If both :fleet and :fleet-filter are specified, they are stored as-is, no consistency check is made."
  [{:keys [fleet fleet-filter overwrites] :as resource}]
  (let [owner-authn   (auth/get-owner-authn resource)
        owner-request {:nuvla/authn owner-authn}
        apps-set-id   (create-module-apps-set resource owner-request)
        fleet         (or fleet (map :id (some-> fleet-filter (utils/query-nuvlaboxes-as owner-authn))))]
    (doseq [edge-id fleet] (retrieve-edge-as edge-id owner-authn))
    (-> resource
        (dissoc :modules :overwrites :fleet :fleet-filter)
        (assoc :applications-sets [{:id      apps-set-id,
                                    :version 0
                                    :overwrites
                                    [(cond-> {:fleet fleet}
                                             fleet-filter (assoc :fleet-filter fleet-filter)
                                             overwrites (merge {:applications overwrites}))]}]))))

(defn create-app-set
  [{:keys [modules] :as resource}]
  (if (some? modules)
    (replace-modules-by-apps-set resource)
    resource))

(defn assoc-operational-status
  [resource request]
  (assoc resource :operational-status (divergence-map resource request)))

(defn assoc-next-refresh
  [{:keys [auto-update-interval] :as resource}]
  (assoc resource :next-refresh
                  (-> (t/now)
                      (t/plus (t/duration-unit (or auto-update-interval 5) :minutes))
                      t/to-str)))

(defn assoc-auto-update-flag
  [{:keys [auto-update next-refresh] :as resource}
   {{:keys [uuid]} :params :as request}]
  (let [current                  (when (and auto-update uuid)
                                   (-> (str resource-type "/" uuid)
                                       crud/retrieve-by-id-as-admin))
        new-auto-update-interval (-> request :body :auto-update-interval)]
    (cond-> resource

            (nil? auto-update)
            (assoc :auto-update false)

            (and auto-update (or (not next-refresh)
                                 (and (some? new-auto-update-interval)
                                      (not= (:auto-update-interval current) new-auto-update-interval))))
            (assoc-next-refresh))))

(defn check-edges-permissions
  [{:keys [id] :as resource}]
  (let [fleet             (get-in resource [:applications-sets 0 :overwrites 0 :fleet])
        missing-edges     (utils/get-missing-edges resource (auth/get-internal-request))
        not-deleted-edges (set/difference (set fleet) (set missing-edges))
        cimi-filter       (str "id=['" (str/join "','" not-deleted-edges) "']")
        retrieved-fleet   (utils/query-nuvlaboxes-as cimi-filter (auth/get-owner-authn resource))]
    (when (not= (count not-deleted-edges) (count retrieved-fleet))
      (throw (r/ex-response "All edges must be visible to DG owner" 403 id)))
    resource))

(defn app-compatible?
  [{dg-subtype :subtype :as _resource}
   {module-subtype :subtype module-compatibility :compatibility}]
  (or (nil? dg-subtype)
      (and (= spec/subtype-docker-compose dg-subtype)
           (= module-spec/subtype-app-docker module-subtype)
           (= module-spec/compatibility-docker-compose module-compatibility))
      (and (= spec/subtype-docker-swarm dg-subtype)
           (= module-spec/subtype-app-docker module-subtype)
           (#{module-spec/compatibility-docker-compose module-spec/compatibility-swarm} module-compatibility))
      (and (= spec/subtype-kubernetes dg-subtype)
           (#{module-spec/subtype-app-k8s module-spec/subtype-app-helm} module-subtype))))

(defn check-apps-compatibility
  [{:keys [id] :as resource} apps]
  (let [compatible? (partial app-compatible? resource)]
    (when-not (every? compatible? apps)
      (let [not-compatible-apps (filter (complement compatible?) apps)]
        (throw (r/ex-response (str "Some apps are not compatible with the DG subtype : "
                                   (mapv :id not-compatible-apps)) 400 id))))
    resource))

(defn pre-validate-hook
  [resource request]
  (let [apps (utils/check-apps-permissions resource)]
    (-> resource
        (check-edges-permissions)
        (check-apps-compatibility apps)
        (assoc-operational-status request)
        (assoc-auto-update-flag request))))

(defn add-edit-pre-validate-hook
  [resource request]
  (-> resource
      (dep-utils/add-api-endpoint request)
      create-app-set
      (pre-validate-hook request)))

(defn add-pre-validate-hook
  [resource request]
  (-> resource
      (assoc :owner (auth/current-active-claim request))
      (add-edit-pre-validate-hook request)))

(defn authn-info-payload
  [resource]
  {:dg-owner-authn-info (auth/get-owner-authn resource)
   :dg-authn-info       (auth/get-resource-id-authn resource)})

(defn action-job-acl
  [{:keys [owner] :as _resource} request]
  (-> {:owners   ["group/nuvla-admin"]
       :view-acl [(auth/current-active-claim request)]}
      (a/acl-append :view-acl owner)
      (a/acl-append :manage owner)))

(defn action-bulk
  [{:keys [id] :as resource} {{:keys [action]} :params :as request}]
  (let [acl (action-job-acl resource request)]
    (job-utils/create-bulk-job
      (utils/bulk-action-job-name action) id request acl
      (authn-info-payload resource))))

(defn action-simple
  [{:keys [id] :as resource} {{:keys [action]} :params :as request}]
  (let [job-action (utils/action-job-name action)
        {{job-id     :resource-id
          job-status :status} :body} (job-utils/create-job id (utils/action-job-name action)
                                                           (action-job-acl resource request)
                                                           (auth/current-user-id request)
                                                           :payload (json/write-str (authn-info-payload resource)))
        job-msg    (str action " on " id " with async " job-id)]
    (if (not= job-status 201)
      (throw (r/ex-response (format "unable to create async job to %s" job-action) 500 id))
      (r/map-response job-msg 202 id job-id))))

(defn resource->json-response
  [resource _request]
  (r/json-response resource))

(defn internal-standard-action
  [current request f]
  (-> current
      (pre-validate-hook request)
      (sm/transition request)
      (utils/save-deployment-set current)
      (f request)))

(defn standard-action
  ([request]
   (standard-action request resource->json-response))
  ([request f]
   (let [current (load-resource-throw-not-allowed-action request)]
     (internal-standard-action current request f))))

(defmethod crud/do-action [resource-type utils/action-plan]
  [request]
  (let [deployment-set    (load-resource-throw-not-allowed-action request)
        owner-request     (auth/get-owner-request deployment-set)
        applications-sets (-> deployment-set
                              utils/get-applications-sets-href
                              (crud/get-resource-throw-nok owner-request))]
    (r/json-response (utils/plan deployment-set applications-sets))))

(defmethod crud/do-action [resource-type utils/action-check-requirements]
  [request]
  (let [deployment-set    (load-resource-throw-not-allowed-action request)
        owner-request     (auth/get-owner-request deployment-set)
        applications-sets (-> deployment-set
                              utils/get-applications-sets-href
                              (crud/get-resource-throw-nok owner-request))]
    (r/json-response (utils/check-requirements deployment-set applications-sets))))

(defn operational-status-content
  [resource _request]
  (-> resource :operational-status r/json-response))

(defmethod crud/do-action [resource-type utils/action-operational-status]
  [request]
  (standard-action request operational-status-content))

(defmethod crud/do-action [resource-type utils/action-start]
  [request]
  (standard-action request action-bulk))

(defmethod crud/do-action [resource-type utils/action-update]
  [request]
  (standard-action request action-bulk))

(defmethod crud/do-action [resource-type utils/action-stop]
  [request]
  (standard-action request action-bulk))

(defn action-ok-nok-transition-fn
  [action-selector]
  (fn [{:keys [target-resource] :as _job}]
    (let [id            (:href target-resource)
          admin-request {:params      (u/id->request-params id)
                         :nuvla/authn auth/internal-identity}
          current       (crud/retrieve-by-id-as-admin id)
          next          (pre-validate-hook current admin-request)
          action        (action-selector next admin-request)]
      (-> next
          (sm/transition (assoc-in admin-request [:params :action] action))
          (utils/save-deployment-set current)))))

(def action-ok-nok-transition-op-status
  (action-ok-nok-transition-fn utils/operational-status-dependent-action))

(def action-ok-nok-transition-all-deps-stopped?
  (action-ok-nok-transition-fn utils/deployments-dependent-action))

(defmethod job-interface/on-timeout [resource-type (utils/bulk-action-job-name utils/action-start)]
  [job]
  (action-ok-nok-transition-op-status job))

(defmethod job-interface/on-timeout [resource-type (utils/bulk-action-job-name utils/action-stop)]
  [job]
  (action-ok-nok-transition-all-deps-stopped? job))

(defmethod job-interface/on-timeout [resource-type (utils/bulk-action-job-name utils/action-update)]
  [job]
  (action-ok-nok-transition-op-status job))

(defmethod job-interface/on-cancel [resource-type (utils/bulk-action-job-name utils/action-start)]
  [job]
  (action-ok-nok-transition-op-status job))

(defmethod job-interface/on-cancel [resource-type (utils/bulk-action-job-name utils/action-stop)]
  [job]
  (action-ok-nok-transition-all-deps-stopped? job))

(defmethod job-interface/on-cancel [resource-type (utils/bulk-action-job-name utils/action-update)]
  [job]
  (action-ok-nok-transition-op-status job))

(defmethod job-interface/on-done [resource-type (utils/bulk-action-job-name utils/action-start)]
  [job]
  (action-ok-nok-transition-op-status job))

(defmethod job-interface/on-done [resource-type (utils/bulk-action-job-name utils/action-stop)]
  [job]
  (action-ok-nok-transition-all-deps-stopped? job))

(defmethod job-interface/on-done [resource-type (utils/bulk-action-job-name utils/action-update)]
  [job]
  (action-ok-nok-transition-op-status job))

(defn job-delete-deployment-set-done
  [{{id :href} :target-resource
    state      :state
    :as        _job}]
  (when (= state job-utils/state-success)
    (let [deployment-set (crud/retrieve-by-id-as-admin id)]
      (db/delete deployment-set))))

(defmethod job-interface/on-done [resource-type (utils/action-job-name crud/action-delete)]
  [job]
  (job-delete-deployment-set-done job))

(defmethod job-interface/on-done [resource-type (utils/action-job-name utils/action-force-delete)]
  [job]
  (job-delete-deployment-set-done job))

(defmethod crud/do-action [resource-type utils/action-force-delete]
  [request]
  (standard-action request action-simple))

(defn dg-subtype-filter
  [{:keys [subtype] :as _deployment-set}]
  (condp = subtype
    spec/subtype-docker-compose
    (nb-utils/coe-filter [nb-spec/coe-type-docker nb-spec/coe-type-swarm])
    spec/subtype-docker-swarm
    (nb-utils/coe-filter [nb-spec/coe-type-swarm])
    spec/subtype-kubernetes
    (nb-utils/coe-filter [nb-spec/coe-type-kubernetes])))

(defn recompute-fleet
  [{:keys [applications-sets owner] :as resource}]
  (let [fleet-filter (-> applications-sets first :overwrites first :fleet-filter)]
    (cond-> resource
            fleet-filter
            (assoc-in [:applications-sets 0 :overwrites 0 :fleet]
                      (map :id (utils/query-nuvlaboxes-as
                                 (str fleet-filter " and " (dg-subtype-filter resource))
                                 {:claims       #{owner "group/nuvla-user"}
                                  :user-id      owner
                                  :active-claim owner}))))))

(defmethod crud/do-action [resource-type utils/action-recompute-fleet]
  [request]
  (-> request
      load-resource-throw-not-allowed-action
      recompute-fleet
      (internal-standard-action request resource->json-response)))

(defmethod crud/do-action [resource-type utils/action-auto-update]
  [request]
  (let [current        (load-resource-throw-not-allowed-action request)
        next           (-> current
                           recompute-fleet
                           assoc-next-refresh
                           (pre-validate-hook request)
                           (utils/save-deployment-set current))
        update-request (assoc-in request [:params :action] utils/action-update)]
    (if (utils/operational-status-nok? next)
      (internal-standard-action next update-request action-bulk)
      (r/map-response "Deployment set is up to date." 200 (:id next)))))

(defn cancel-latest-job
  [{:keys [id] :as _resource} _request]
  (if-let [job-id (job-utils/existing-job-id-not-in-final-state id)]
    (do (crud/do-action-as-admin job-id job-utils/action-cancel)
        (r/map-response "operation cancelled" 200))
    (r/map-response "no running operation found that can be cancelled" 404)))

(defmethod crud/do-action [resource-type utils/action-cancel]
  [request]
  (standard-action request cancel-latest-job))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type
                               :pre-validate-hook add-pre-validate-hook))

(defmethod crud/add resource-type
  [{{:keys [start]} :body :as request}]
  (let [response (add-impl request)
        id       (get-in response [:body :resource-id])]
    (if start
      (crud/do-action {:params      {:resource-name resource-type
                                     :uuid          (u/id->uuid id)
                                     :action        utils/action-start}
                       :nuvla/authn auth/internal-identity})
      response)))

(def retrieve-impl (std-crud/retrieve-fn resource-type))

(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))

(def edit-impl (std-crud/edit-fn resource-type
                                 :immutable-keys [:owner]
                                 :pre-validate-hook add-edit-pre-validate-hook))

(defmethod crud/edit resource-type
  [{{uuid :uuid} :params :as request}]
  (let [current (-> (str resource-type "/" uuid)
                    crud/retrieve-by-id-as-admin
                    (a/throw-cannot-edit request))
        resp    (edit-impl request)
        next    (:body resp)]
    (when (not= (:name current) (:name next))
      (deployment/bulk-update-deployment-set-name-as-admin next))
    resp))

(defmethod crud/delete resource-type
  [request]
  (standard-action request action-simple))

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))

(defmethod crud/query resource-type
  [request]
  (query-impl request))

(defmethod crud/set-operations resource-type
  [resource request]
  (if-let [operations (seq (utils/get-operations resource request))]
    (assoc resource :operations operations)
    resource))

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::spec/deployment-set))

(defn initialize
  []
  (std-crud/initialize resource-type ::spec/deployment-set)
  (md/register resource-metadata))
