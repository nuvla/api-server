(ns sixsq.nuvla.server.resources.deployment-set
  "
These resources represent a deployment set that regroups deployments.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.state-machine :as sm]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment-set.operational-status :as os]
    [sixsq.nuvla.server.resources.deployment-set.utils :as utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.job.interface :as job-interface]
    [sixsq.nuvla.server.resources.job.utils :as job-utils]
    [sixsq.nuvla.server.resources.module.utils :as module-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.deployment-set :as spec]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))

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
  [resource request]
  (a/add-acl resource request))

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
  ([{:keys [applications-sets] :as deployment-set} request]
   (when (seq applications-sets)
     (let [applications-sets (-> deployment-set
                                 utils/get-applications-sets-href
                                 (crud/get-resource-throw-nok request))
           divergence        (os/divergence-map
                               (utils/plan deployment-set applications-sets)
                               (utils/current-state deployment-set))
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

(defn retrieve-module
  [id request]
  (:body (crud/retrieve {:params         {:uuid          (u/id->uuid id)
                                          :resource-name module-utils/resource-type}
                         :request-method :get
                         :nuvla/authn    (auth/current-authentication request)})))

(defn create-module-apps-set
  [{:keys [modules]} request]
  (create-module
    {:path    (str module-utils/project-apps-sets "/" (u/random-uuid))
     :subtype module-utils/subtype-apps-sets
     :acl     {:owners [(auth/current-active-claim request)]}
     :content {:commit "no commit message"
               :author (auth/current-active-claim request)
               :applications-sets
               [{:name         "Main"
                 :applications (map #(hash-map :id (module-utils/full-uuid->uuid %)
                                               :version
                                               (module-utils/latest-or-version-index
                                                 (retrieve-module % request) %))
                                    modules)}]}}))

(defn replace-modules-by-apps-set
  [{:keys [fleet] :as resource} request]
  (let [apps-set-id (create-module-apps-set resource request)]
    (-> resource
        (dissoc :modules :fleet)
        (assoc :applications-sets [{:id      apps-set-id,
                                    :version 0
                                    :overwrites
                                    [{:fleet fleet}]}]))))

(defn create-app-set
  [{:keys [modules] :as resource} request]
  (if (seq modules)
    (replace-modules-by-apps-set resource request)
    resource))

(defn pre-validate-hook
  [resource request]
  (assoc resource :operational-status (divergence-map resource request)))

(defn add-pre-validate-hook
  [resource request]
  (-> resource
      (create-app-set request)
      (pre-validate-hook request)))

(defn action-bulk
  [{:keys [id] :as _resource} {{:keys [action]} :params :as request}]
  (let [authn-info (auth/current-authentication request)
        acl        {:owners   ["group/nuvla-admin"]
                    :view-acl [(auth/current-active-claim request)]}]
    (std-crud/create-bulk-job
      (utils/bulk-action-job-name action) id authn-info acl {})))

(defn action-simple
  [{:keys [id] :as _resource} {{:keys [action]} :params :as request}]
  (let [job-action (utils/action-job-name action)
        {{job-id     :resource-id
          job-status :status} :body} (job/create-job id (utils/action-job-name action)
                                                     {:owners   ["group/nuvla-admin"]
                                                      :view-acl [(auth/current-active-claim request)]})
        job-msg    (str action " on " id " with async " job-id)]
    (if (not= job-status 201)
      (throw (r/ex-response (format "unable to create async job to %s" job-action) 500 id))
      (r/map-response job-msg 202 id job-id))))

(defn standard-action
  ([request]
   (standard-action request (fn [resource _request] resource)))
  ([request f]
   (let [current (load-resource-throw-not-allowed-action request)]
     (-> current
         (pre-validate-hook request)
         (sm/transition request)
         (utils/save-deployment-set current)
         (f request)))))

(defn state-transition
  [id action]
  (standard-action {:params      (assoc (u/id->request-params id)
                                   :action action)
                    :nuvla/authn auth/internal-identity}))

(defmethod crud/do-action [resource-type utils/action-plan]
  [request]
  (let [deployment-set    (load-resource-throw-not-allowed-action request)
        applications-sets (-> deployment-set
                              utils/get-applications-sets-href
                              (crud/get-resource-throw-nok request))]
    (r/json-response (utils/plan deployment-set applications-sets))))

(defmethod crud/do-action [resource-type utils/action-operational-status]
  [request]
  (r/json-response (divergence-map request)))

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
      (db/delete deployment-set
                 {:request-method :delete
                  :params         (assoc (u/id->request-params id)
                                    :action crud/action-delete)
                  :authn-info     auth/internal-identity}))))

(defmethod job-interface/on-done [resource-type (utils/action-job-name crud/action-delete)]
  [job]
  (job-delete-deployment-set-done job))

(defmethod job-interface/on-done [resource-type (utils/action-job-name utils/action-force-delete)]
  [job]
  (job-delete-deployment-set-done job))

(defmethod crud/do-action [resource-type utils/action-force-delete]
  [request]
  (standard-action request action-simple))

(defn cancel-latest-job
  [{:keys [id] :as _resource} _request]
  (let [filter-str (format "target-resource/href='%s' and (state='%s' or state='%s')" id
                           job-utils/state-queued job-utils/state-running)
        [_ [{job-id :id}]]
        (crud/query-as-admin
          job/resource-type
          {:cimi-params {:filter  (parser/parse-cimi-filter filter-str)
                         :orderby [["created" :desc]]
                         :last    1}})]
    (crud/do-action-as-admin job-id job-utils/action-cancel))
  (r/map-response "operation canceled" 200))

(defmethod crud/do-action [resource-type utils/action-cancel]
  [request]
  (standard-action request cancel-latest-job))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type add-pre-validate-hook))

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

(def edit-impl (std-crud/edit-fn resource-type :pre-validate-hook pre-validate-hook))

(defmethod crud/edit resource-type
  [request]
  (edit-impl request))

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
