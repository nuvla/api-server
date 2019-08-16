(ns sixsq.nuvla.server.resources.deployment-log
  "
These resources represent the logs of a deployment.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.deployment-log :as dl]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-admin"]})


(def actions [{:name             "fetch"
               :uri              "fetch"
               :description      "fetches the next set of lines from the log"
               :method           "POST"
               :input-message    "application/json"
               :output-message   "application/json"}])

;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


(def validate-fn (u/create-spec-validation-fn ::dl/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{:keys [body] :as request}]
  (let [updated-body (dissoc body :parent :service)]
    (edit-impl (assoc request :body updated-body))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


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
  (let [fetch-op    (u/action-map id :fetch)
        can-manage? (a/can-manage? resource request)]
    (cond-> (crud/set-standard-operations resource request)
            can-manage? (update :operations conj fetch-op))))


(defn create-job
  [job-type {{uuid :uuid} :params :as request}]
  (try
    (let [id       (str resource-type "/" uuid)
          resource (crud/retrieve-by-id-as-admin id)]
      (a/throw-cannot-manage resource request)

      (if-let [session-id (auth/current-session-id request)]
        (let [
              {{job-id     :resource-id
                job-status :status} :body} (job/create-job id (str job-type "_deployment_log")
                                                           {:owners   ["group/nuvla-admin"]
                                                            :view-acl [session-id]}
                                                           :priority 50)
              job-msg (str "starting " id " with async " job-id)]
          (when (not= job-status 201)
            (throw (r/ex-response (format "unable to create async job to % log" job-type) 500 id)))
          (r/map-response job-msg 202 id job-id))
        (throw (r/ex-response "current authentication has no session identifier" 500 id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "fetch"]
  [{{uuid :uuid} :params :as request}]
  (create-job "fetch" request))


;;
;; internal crud
;;

(defn create-log
  [deployment-id session-id service & [{:keys [since lines]}]]
  (let [acl            {:owners    ["group/nuvla-admin"]
                        :edit-data [session-id]
                        :manage    [session-id]}
        log-map        (cond-> {:parent  deployment-id
                                :service service
                                :acl     acl}
                               since (assoc :since since)
                               lines (assoc :lines lines))
        create-request {:params      {:resource-name resource-type}
                        :body        log-map
                        :nuvla/authn auth/internal-identity}]
    (crud/add create-request)))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::dl/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::dl/schema)
  (md/register resource-metadata))
