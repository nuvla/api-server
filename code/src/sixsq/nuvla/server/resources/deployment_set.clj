(ns sixsq.nuvla.server.resources.deployment-set
  "
These resources represent a deployment set that regroups deployments.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.state-machine :as sm]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment-set.operational-status :as os]
    [sixsq.nuvla.server.resources.deployment-set.utils :as utils]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
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

(defn action-bulk
  [{:keys [id] :as _resource} {{:keys [action]} :params :as request}]
  (let [authn-info (auth/current-authentication request)
        acl        {:owners   ["group/nuvla-admin"]
                    :view-acl [(auth/current-active-claim request)]}
        payload    {:filter (str "deployment-set='" id "'")}]
    (event-utils/create-event id action (a/default-acl authn-info))
    (std-crud/create-bulk-job
      (utils/action-job-name action) id authn-info acl payload)))

(defn load-resource-throw-not-allowed-action
  [{{:keys [uuid]} :params :as request}]
  (-> (str resource-type "/" uuid)
      crud/retrieve-by-id-as-admin
      (a/throw-cannot-manage request)
      (sm/throw-can-not-do-action request)))

(defn standard-action
  [request f]
  (let [resource (-> request
                     load-resource-throw-not-allowed-action
                     (sm/transition request)
                     utils/save-deployment-set
                     :body)]
    (f resource request)))

(defmethod crud/do-action [resource-type utils/action-plan]
  [request]
  (let [deployment-set    (load-resource-throw-not-allowed-action request)
        applications-sets (-> deployment-set
                              utils/get-applications-sets-href
                              (crud/get-resource-throw-nok request))]
    (r/json-response (utils/plan deployment-set applications-sets))))

(defn divergence-map
  [request]
  (let [deployment-set    (load-resource-throw-not-allowed-action request)
        applications-sets (-> deployment-set
                              utils/get-applications-sets-href
                              (crud/get-resource-throw-nok request))
        divergence        (os/divergence-map
                            (utils/plan deployment-set applications-sets)
                            (utils/current-state deployment-set))
        status            (if (some (comp pos? count) (vals divergence))
                            "NOK"
                            "OK")]
    (assoc divergence :status status)))

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


(defn job-transition
  [{:keys [target-resource] :as _job}]
  (let [request {:params      (u/id->request-params (:href target-resource))
                 :nuvla/authn auth/internal-identity}
        {:keys [status]} (divergence-map (assoc-in request [:params :action] utils/action-operational-status))]
    (if (= "OK" status)
      (standard-action (assoc-in request [:params :action] utils/action-ok)
                       (fn [_resource _request]
                         (r/map-response "running action done" 200)))
      (standard-action (assoc-in request [:params :action] utils/action-nok)
                       (fn [_resource _request]
                         (r/map-response "running action done" 200))))))

(defmethod job-interface/on-timeout [resource-type "bulk_deployment_set_start"]
  [job]
  (job-transition job))

(defmethod job-interface/on-timeout [resource-type "bulk_deployment_set_stop"]
  [job]
  (job-transition job))

(defmethod job-interface/on-timeout [resource-type "bulk_deployment_set_update"]
  [job]
  (job-transition job))

(defmethod job-interface/on-cancel [resource-type "bulk_deployment_set_start"]
  [job]
  (job-transition job))

(defmethod job-interface/on-cancel [resource-type "bulk_deployment_set_stop"]
  [job]
  (job-transition job))

(defmethod job-interface/on-cancel [resource-type "bulk_deployment_set_update"]
  [job]
  (job-transition job))

(defmethod job-interface/on-done [resource-type "bulk_deployment_set_start"]
  [job]
  (job-transition job))

(defmethod job-interface/on-done [resource-type "bulk_deployment_set_stop"]
  [job]
  (job-transition job))

(defmethod job-interface/on-done [resource-type "bulk_deployment_set_update"]
  [job]
  (job-transition job))

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
  (let [resource (load-resource-throw-not-allowed-action request)]
    (cancel-latest-job resource request)))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defn retrieve-module
  [id request]
  (:body (crud/retrieve {:params         {:uuid          (u/id->uuid id)
                                          :resource-name module-utils/resource-type}
                         :request-method :get
                         :nuvla/authn    (auth/current-authentication request)})))

(defn create-module
  [module]
  (let [{{:keys [status resource-id]} :body
         :as                          response} (module-utils/create-module module)]
    (if (= status 201)
      resource-id
      (log/errorf "unexpected status code (%s) when creating %s resource: %s"
                  (str status) module response))))

(defn create-module-apps-set
  [{{:keys [modules]} :body :as request}]
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
  [{{:keys [fleet] :as body} :body :as request}]
  (let [apps-set-id (create-module-apps-set request)
        new-body    (-> body
                        (dissoc :modules :fleet)
                        (assoc :applications-sets [{:id      apps-set-id,
                                                    :version 0
                                                    :overwrites
                                                    [{:fleet fleet}]}]))]
    (assoc request :body new-body)))

(defn request-with-create-app-set
  [{{:keys [modules]} :body :as request}]
  (if (seq modules)
    (replace-modules-by-apps-set request)
    request))

(defmethod crud/add resource-type
  [{{:keys [start]} :body :as request}]
  (let [response (-> request
                     request-with-create-app-set
                     add-impl)
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

(def edit-impl (std-crud/edit-fn resource-type))

(defmethod crud/edit resource-type
  [request]
  (edit-impl request))

(def delete-impl (std-crud/delete-fn resource-type))

(defmethod crud/delete resource-type
  [{{uuid :uuid} :params :as request}]
  (-> (str resource-type "/" uuid)
      crud/retrieve-by-id-as-admin
      (a/throw-cannot-delete request)
      (sm/throw-can-not-do-action request))
  ;; todo : need to delete deployments
  (delete-impl request))

;; todo : action force delete

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
