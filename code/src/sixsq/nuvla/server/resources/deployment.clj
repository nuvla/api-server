(ns sixsq.nuvla.server.resources.deployment
  "
These resources represent the deployment of a component or application within
a container orchestration engine.
"
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.acl-resource :as acl-resource]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.customer :as customer]
    [sixsq.nuvla.server.resources.deployment.utils :as dep-utils]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.deployment :as deployment-spec]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


(def actions [{:name           "start"
               :uri            "start"
               :description    "starts the deployment"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "stop"
               :uri            "stop"
               :description    "stops the deployment"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name             "create-log"
               :uri              "create-log"
               :description      (str "creates a new deployment-log resource "
                                      "to collect logging information")
               :method           "POST"
               :input-message    "application/json"
               :output-message   "application/json"

               :input-parameters [{:name "service"}

                                  {:name "since"}

                                  {:name        "lines"
                                   :value-scope {:minimum 1
                                                 :default 200}}]}

              {:name           "update"
               :uri            "update"
               :description    "update the deployment image"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}

              {:name           "clone"
               :uri            "clone"
               :description    "clone the deployment"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}


              {:name           "fetch-module"
               :uri            "fetch-module"
               :description    "fetch the deployment module href and merge it"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}])


;;
;; validate deployment
;;

(def validate-fn (u/create-spec-validation-fn ::deployment-spec/deployment))


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

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defn create-deployment
  [{:keys [base-uri] {:keys [owner]} :body :as request}]
  (a/throw-cannot-add collection-acl request)
  (customer/throw-user-hasnt-active-subscription request)
  (let [authn-info      (auth/current-authentication request)
        is-admin?       (acl-resource/is-admin? authn-info)
        dep-owner       (if is-admin? (or owner "group/nuvla-admin")
                                      (auth/current-active-claim request))
        deployment      (-> request
                            (dep-utils/create-deployment)
                            (assoc :resource-type resource-type
                                   :state "CREATED"
                                   :api-endpoint (str/replace-first base-uri #"/api/" "")
                                   :owner dep-owner))
        ;; FIXME: Correct the value passed to the python API.

        create-response (add-impl (assoc request :body deployment))

        deployment-id   (get-in create-response [:body :resource-id])

        msg             (get-in create-response [:body :message])]

    (event-utils/create-event deployment-id msg (a/default-acl authn-info))

    (dep-utils/assoc-api-credentials deployment-id authn-info)

    create-response))

(defmethod crud/add resource-type
  [request]
  (create-deployment request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{{:keys [acl parent]} :body {uuid :uuid} :params :as request}]
  (let [authn-info (auth/current-authentication request)
        is-user?   (not (acl-resource/is-admin? authn-info))
        new-acl    (when (and is-user? acl)
                     (if-let [current-owner (-> (str resource-type "/" uuid)
                                                (db/retrieve request)
                                                :owner)]
                       (assoc acl :owners (-> acl :owners set (conj current-owner) vec))
                       acl))
        infra-id   (some-> parent (crud/retrieve-by-id {:nuvla/authn authn-info}) :parent)]

    (edit-impl
      (cond-> request
              is-user? (update :body dissoc :owner :infrastructure-service)
              new-acl (assoc-in [:body :acl] new-acl)
              infra-id (assoc-in [:body :infrastructure-service] infra-id)))))


(defn delete-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (let [deployment-id   (str resource-type "/" uuid)
          delete-response (-> deployment-id
                              (db/retrieve request)
                              (dep-utils/throw-can-not-do-action dep-utils/can-delete? "delete")
                              (a/throw-cannot-delete request)
                              (db/delete request))]
      (dep-utils/delete-all-child-resources deployment-id)
      delete-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [start-op        (u/action-map id :start)
        stop-op         (u/action-map id :stop)
        update-op       (u/action-map id :update)
        create-log-op   (u/action-map id :create-log)
        clone-op        (u/action-map id :clone)
        fetch-module-op (u/action-map id :fetch-module)
        can-manage?     (a/can-manage? resource request)
        can-clone?      (a/can-view-data? resource request)]
    (cond-> (crud/set-standard-operations resource request)

            (and can-manage? (dep-utils/can-start? resource)) (update :operations conj start-op)

            (and can-manage? (dep-utils/can-stop? resource))
            (update :operations conj stop-op)

            (and can-manage? (dep-utils/can-update? resource)) (update :operations conj update-op)

            (and can-manage? (dep-utils/can-create-log? resource))
            (update :operations conj create-log-op)

            (and can-manage? can-clone?)
            (update :operations conj clone-op)

            (and can-manage? (dep-utils/can-fetch-module? resource))
            (update :operations conj fetch-module-op)

            (not (dep-utils/can-delete? resource))
            (update :operations dep-utils/remove-delete))))


(defn edit-deployment
  [resource request edit-fn]
  (-> resource
      (edit-fn)
      (u/update-timestamps)
      (u/set-updated-by request)
      (db/edit request)))


(defmethod crud/do-action [resource-type "start"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id             (str resource-type "/" uuid)
          deployment     (crud/retrieve-by-id-as-admin id)
          new-deployment (-> deployment
                             (dep-utils/throw-can-not-do-action dep-utils/can-start? "start")
                             (edit-deployment request #(assoc % :state "STARTING"))
                             :body)]
      (when (= (:state deployment) "STOPPED")
        (dep-utils/delete-child-resources "deployment-parameter" id))
      (dep-utils/create-job new-deployment request "start"))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "stop"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        (crud/retrieve-by-id-as-admin)
        (dep-utils/throw-can-not-do-action dep-utils/can-stop? "stop")
        (edit-deployment request #(assoc % :state "STOPPING"))
        :body
        (dep-utils/create-job request "stop"))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "create-log"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        (crud/retrieve-by-id-as-admin)
        (a/throw-cannot-manage request)
        (dep-utils/throw-can-not-do-action dep-utils/can-create-log? "create-log")
        (dep-utils/create-log request))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "fetch-module"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        (crud/retrieve-by-id-as-admin)
        (a/throw-cannot-manage request)
        (dep-utils/throw-can-not-do-action dep-utils/can-fetch-module? "fetch-module")
        (dep-utils/fetch-module request)
        (edit-deployment request identity))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "clone"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (crud/retrieve-by-id-as-admin id)
          (a/throw-cannot-view-data request))
      (create-deployment (assoc-in request [:body :deployment :href] id)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn update-deployment-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        (crud/retrieve-by-id-as-admin)
        (edit-deployment request #(assoc % :state "UPDATING"))
        :body
        (dep-utils/create-job request "update"))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type "update"]
  [request]
  (update-deployment-impl request))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::deployment-spec/deployment))


(defn initialize
  []
  (std-crud/initialize resource-type ::deployment-spec/deployment)
  (md/register resource-metadata))
