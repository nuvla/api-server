(ns sixsq.nuvla.server.resources.deployment-set
  "
These resources represent a deployment set that regroups deployments.
"
  (:require
    [clojure.data.json :as json]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
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

(def actions [{:name           "create"
               :uri            "create"
               :description    "create deployments"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "start"
               :uri            "start"
               :description    "start deployment set"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "stop"
               :uri            "stop"
               :description    "stop deployment set"
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

(defn can-start?
  [{:keys [state] :as _resource}]
  (contains? #{"CREATED" "STOPPED"} state))

(defn can-stop?
  [{:keys [state] :as _resource}]
  (contains? #{"STARTED"} state))

(defn create-job
  [{:keys [id] :as resource} request action]
  (a/throw-cannot-manage resource request)
  (let [authn-info   (auth/current-authentication request)
        active-claim (auth/current-active-claim request)
        {{job-id     :resource-id
          job-status :status} :body} (job/create-job
                                       id action
                                       {:owners   ["group/nuvla-admin"]
                                        :edit-acl [active-claim]}
                                       :payload (json/write-str
                                                  {:authn-info authn-info}))
        job-msg      (str action " " id " with async " job-id)]
    (when (not= job-status 201)
      (throw (r/ex-response
               (format "unable to create async job to %s deployment set" action) 500 id)))
    (event-utils/create-event id job-msg (a/default-acl (auth/current-authentication request)))
    (r/map-response job-msg 202 id job-id)))

(defn create
  [id request]
  (-> id
      crud/retrieve-by-id-as-admin
      (a/throw-cannot-manage request)
      (create-job request "create_deployment_set")))

(defmethod crud/do-action [resource-type "create"]
  [{{uuid :uuid} :params :as request}]
  (create (str resource-type "/" uuid) request))

(defn throw-can-not-do-action
  [{:keys [id state] :as resource} pred action]
  (if (pred resource)
    resource
    (throw (r/ex-response (format "invalid state (%s) for %s on %s"
                                  state action id) 409 id))))

(defn action-bulk
  [{{uuid :uuid} :params :as request} action can-action? action-filter]
  (let [{:keys [id]} (-> (str resource-type "/" uuid)
                         crud/retrieve-by-id-as-admin
                         (a/throw-cannot-manage request)
                         (throw-can-not-do-action can-action? action))
        authn-info (auth/current-authentication request)
        acl        {:owners   ["group/nuvla-admin"]
                    :view-acl [(auth/current-active-claim request)]}
        payload    {:filter action-filter}]
<<<<<<< HEAD
=======
    (event-utils/create-event id action (a/default-acl authn-info))
>>>>>>> master
    (std-crud/create-bulk-job
      (str action "_deployment_set") id authn-info acl payload)))

(defmethod crud/do-action [resource-type "start"]
  [{{uuid :uuid} :params :as request}]
  (let [id            (str resource-type "/" uuid)
        action-filter (str "deployment-set='" id "' and (state='CREATED' or state='STOPPED')")]
    (action-bulk request "start" can-start? action-filter)))

(defmethod crud/do-action [resource-type "stop"]
  [{{uuid :uuid} :params :as request}]
  (let [id            (str resource-type "/" uuid)
        action-filter (str "deployment-set='" id "'")]      ;;fixme missing filter state
    (action-bulk request "stop" can-stop? action-filter)))

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [request]
  (-> request
      (update :body assoc :state "CREATING")
      add-impl
      (get-in [:body :resource-id])
      (create request)))

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
        can-manage? (a/can-manage? resource request)]
    (cond-> (crud/set-standard-operations resource request)

            can-manage? (update :operations conj create-op)

            (and can-manage? (can-start? resource))
            (update :operations conj start-op)

            (and can-manage? (can-stop? resource))
            (update :operations conj stop-op))))

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::spec/deployment-set))

(defn initialize
  []
  (std-crud/initialize resource-type ::spec/deployment-set)
  (md/register resource-metadata))
