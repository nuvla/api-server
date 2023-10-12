(ns sixsq.nuvla.server.resources.resource-log
  "
These resources represent the logs of a deployment or of a nuvlabox.
"
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.job.interface :as job-interface]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.resource-log :as rl]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))

(def ^:const collection-type (u/ns->collection-type *ns*))

(def ^:const fetch "fetch")

(def ^:const action-suffix "log")

(def action-name #(str/join "_" [fetch % action-suffix]))

(def ^:const fetch-nuvlabox-log (action-name "nuvlabox"))

(def ^:const fetch-deployment-log (action-name "deployment"))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-admin"]
                     :bulk-delete ["group/nuvla-admin"]})


(def actions [{:name           fetch
               :uri            fetch
               :description    "fetches the next set of lines from the log"
               :method         "POST"
               :input-message  "application/json"
               :output-message "application/json"}])

;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


(def validate-fn (u/create-spec-validation-fn ::rl/schema))


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
  (let [updated-body (dissoc body :parent)]
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


(def bulk-delete-impl (std-crud/bulk-delete-fn resource-type collection-acl collection-type))


(defmethod crud/bulk-delete resource-type
  [request]
  (bulk-delete-impl request))


(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [fetch-op    (u/action-map id :fetch)
        can-manage? (a/can-manage? resource request)]
    (cond-> (crud/set-standard-operations resource request)
            can-manage? (update :operations conj fetch-op))))


(defn parent->action-name
  [{:keys [parent] :as _resource-log}]
  (case (u/id->resource-type parent)
    "nuvlabox" fetch-nuvlabox-log
    "deployment" fetch-deployment-log))


(defmulti create-specific-job
          (fn [resource-log _parent-resource _request]
            (parent->action-name resource-log)))


(defmethod create-specific-job fetch-nuvlabox-log
  [{log-id :id :as _resource-log}
   {:keys [id] :as parent-resource}
   request]
  (job/create-job
    log-id fetch-nuvlabox-log
    {:owners    ["group/nuvla-admin"]
     :edit-data [id]
     :manage    [id]
     :view-acl  [(auth/current-session-id request)]}
    :priority 50
    :execution-mode (nb-utils/get-execution-mode parent-resource)))


(defmethod create-specific-job fetch-deployment-log
  [{log-id :id :as _resource-log}
   {:keys [execution-mode nuvlabox] :as _parent-resource}
   request]
  (job/create-job log-id fetch-deployment-log
                  (cond-> {:owners   ["group/nuvla-admin"]
                           :view-acl [(auth/current-session-id request)]}
                          nuvlabox (-> (a/acl-append :edit-data nuvlabox)
                                       (a/acl-append :manage nuvlabox)))
                  :priority 50
                  :execution-mode execution-mode))


(defn create-job
  [{log-id :id :as resource-log}
   parent-resource
   request]
  (try
    (let [job     (create-specific-job resource-log parent-resource request)
          {{job-id     :resource-id
            job-status :status} :body} job
          job-msg (str "starting " log-id " with async " job-id)]
      (when (not= job-status 201)
        (throw
          (r/ex-response
            (format "unable to create async job to %s log" action-name)
            500 log-id)))
      (r/map-response job-msg 202 log-id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn already-job-exist
  [{log-id :id :as resource-log} _request]
  (try
    (let [action-name      (parent->action-name resource-log)
          filter           (format
                             "action='%s' and target-resource/href='%s' and %s"
                             action-name
                             log-id
                             "(state='QUEUED' or state='RUNNING')")
          entries          (second
                             (crud/query-as-admin
                               job/resource-type
                               {:cimi-params
                                {:filter (parser/parse-cimi-filter filter)}}))
          alive-jobs-count (count entries)]
      (when (pos? alive-jobs-count)
        (let [job-id (-> entries first :id)]
          (r/map-response
            (format "existing async %s for %s" job-id log-id)
            202 log-id job-id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn retrieve-parent-resource
  [{:keys [parent] :as _resource-log}]
  (crud/retrieve-by-id-as-admin parent))


(defmethod crud/do-action [resource-type fetch]
  [{{uuid :uuid} :params :as request}]
  (let [id              (str resource-type "/" uuid)
        resource-log    (crud/retrieve-by-id-as-admin id)
        parent-resource (retrieve-parent-resource resource-log)]
    (a/throw-cannot-manage resource-log request)
    (a/throw-cannot-manage parent-resource request)
    (if-let [response (already-job-exist resource-log request)]
      response
      (create-job resource-log parent-resource request))))


(defmethod job-interface/get-context [resource-type fetch-nuvlabox-log]
  [{:keys [target-resource] :as _resource}]
  (let [nuvlabox-log (some-> target-resource :href crud/retrieve-by-id-as-admin)
        nuvlabox     (some-> nuvlabox-log :parent crud/retrieve-by-id-as-admin)]
    (job-interface/get-context->response
      nuvlabox-log
      nuvlabox)))


(defmethod job-interface/get-context [resource-type fetch-deployment-log]
  [{:keys [target-resource] :as _resource}]
  (let [deployment-log (some-> target-resource :href crud/retrieve-by-id-as-admin)
        deployment     (some-> deployment-log :parent crud/retrieve-by-id-as-admin)
        credential     (some-> deployment :parent crud/retrieve-by-id-as-admin)
        infra          (some-> credential :parent crud/retrieve-by-id-as-admin)]
    (job-interface/get-context->response
      deployment
      credential
      infra)))

;;
;; internal crud
;;

(defn create-log
  [parent components acl & [{:keys [since lines]}]]
  (let [log-map        (cond-> {:parent     parent
                                :components components
                                :acl        acl}
                               since (assoc :since since)
                               lines (assoc :lines lines))
        create-request {:params      {:resource-name resource-type}
                        :body        log-map
                        :nuvla/authn auth/internal-identity}]
    (crud/add create-request)))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::rl/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::rl/schema)
  (md/register resource-metadata))
