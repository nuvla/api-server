(ns sixsq.nuvla.server.resources.deployment-set
  "
These resources represent a deployment set that regroups deployments.
"
  (:require
    [clojure.data.json :as json]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment-set.utils :as utils]
    [sixsq.nuvla.server.resources.module.utils :as module-utils]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.deployment-set :as spec]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const collection-type (u/ns->collection-type *ns*))

(def ^:const create-type (u/ns->create-type *ns*))

(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})

(def actions [{:name           utils/action-create
               :uri            utils/action-create
               :description    "create deployments"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           utils/action-start
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

              {:name           utils/action-plan
               :uri            utils/action-plan
               :description    "get an action plan for deployment set"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}])

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

(defn create-job
  [{:keys [id] :as resource} request action]
  (a/throw-cannot-manage resource request)
  (let [authn-info   (auth/current-authentication request)
        active-claim (auth/current-active-claim request)
        {{job-id     :resource-id
          job-status :status} :body} (job/create-job
                                       id (utils/action-job-name action)
                                       {:owners   ["group/nuvla-admin"]
                                        :edit-acl [active-claim]}
                                       :payload (json/write-str
                                                  {:authn-info authn-info}))
        job-msg      (str action " " id " with async " job-id)]
    (when (not= job-status 201)
      (throw (r/ex-response
               (format "unable to create async job to %s deployment set" action) 500 id)))
    (event-utils/create-event id action (a/default-acl (auth/current-authentication request)))
    (r/map-response job-msg 202 id job-id)))

(defn throw-action-not-allowed
  [{{uuid :uuid} :params :as request} can-action? action]
  (-> (str resource-type "/" uuid)
      crud/retrieve-by-id-as-admin
      (a/throw-cannot-manage request)
      (utils/throw-can-not-do-action can-action? action)))

(defn action-bulk
  [id request action]
  (let [authn-info (auth/current-authentication request)
        acl        {:owners   ["group/nuvla-admin"]
                    :view-acl [(auth/current-active-claim request)]}
        payload    {:filter (str "deployment-set='" id "'")}]
    (event-utils/create-event id action (a/default-acl authn-info))
    (std-crud/create-bulk-job
      (utils/action-job-name action) id authn-info acl payload)))

(defmethod crud/do-action [resource-type utils/action-create]
  [{{uuid :uuid} :params :as request}]
  (-> (str resource-type "/" uuid)
      crud/retrieve-by-id-as-admin
      (a/throw-cannot-manage request)
      (utils/throw-can-not-do-action utils/can-create? utils/action-create)
      (create-job request utils/action-create)))

(defmethod crud/do-action [resource-type utils/action-plan]
  [{{uuid :uuid} :params :as request}]
  (let [id                (str resource-type "/" uuid)
        deployment-set    (-> id
                              crud/retrieve-by-id-as-admin
                              (a/throw-cannot-manage request))
        applications-sets (-> deployment-set
                              utils/get-applications-sets-href
                              (crud/get-resource-throw-nok request))]
    (r/json-response (utils/plan deployment-set applications-sets))))

(defmethod crud/do-action [resource-type utils/action-start]
  [{{uuid :uuid} :params :as request}]
  (let [id             (str resource-type "/" uuid)
        deployment-set (-> (crud/retrieve-by-id-as-admin id)
                           (a/throw-cannot-manage request)
                           (utils/throw-can-not-do-action
                             utils/can-start? utils/action-start))]
    (if (utils/state-new? deployment-set)
      (do
        (crud/edit-by-id-as-admin id {:start true})
        (create-job deployment-set request utils/action-create))
      (action-bulk id request utils/action-start))))

(defmethod crud/do-action [resource-type utils/action-stop]
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)]
    (action-bulk id request utils/action-stop)))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defn retrieve-module
  [id request]
  (:body (crud/retrieve {:params         {:uuid          (u/id->uuid id)
                                          :resource-name module-utils/resource-type}
                         :request-method :get
                         :nuvla/authn    (auth/current-authentication request)})))

(defn module-version
  [id request]
  (module-utils/latest-published-or-latest-index
    (retrieve-module id request)))

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
                 :applications (map #(hash-map :id %
                                               :version (module-version % request))
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
                     (update :body assoc :state utils/state-new)
                     add-impl)
        id       (get-in response [:body :resource-id])]
    (if start
      (create-job (crud/get-resource-throw-nok id request)
                  request utils/action-create)
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
  [request]
  (delete-impl request))

(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))

(defmethod crud/query resource-type
  [request]
  (query-impl request))

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [create-op   (u/action-map id :create)
        start-op    (u/action-map id :start)
        stop-op     (u/action-map id :stop)
        plan-op     (u/action-map id :plan)
        can-manage? (a/can-manage? resource request)]
    (cond-> (crud/set-standard-operations resource request)

            can-manage?
            (update :operations conj plan-op)

            (and can-manage? (utils/can-create? resource))
            (update :operations conj create-op)

            (and can-manage? (utils/can-start? resource))
            (update :operations conj start-op)

            (and can-manage? (utils/can-stop? resource))
            (update :operations conj stop-op))))

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::spec/deployment-set))

(defn initialize
  []
  (std-crud/initialize resource-type ::spec/deployment-set)
  (md/register resource-metadata))
