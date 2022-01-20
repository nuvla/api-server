(ns sixsq.nuvla.server.resources.nuvlabox-log
  "
These resources represent the logs of a nuvlabox.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [clojure.pprint :refer [pprint]]

    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.job.interface :as job-interface]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-log :as nl]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-admin"]
                     :bulk-delete ["group/nuvla-admin"]})


(def actions [{:name           "fetch"
               :uri            "fetch"
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


(def validate-fn (u/create-spec-validation-fn ::nl/schema))


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


(defn create-job
  [job-type {:keys [nb-id nb-acl] :as nuvlabox} {{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (pprint (-> nb-acl
                (a/acl-append :edit-data nb-id)
                (a/acl-append :manage nb-id)))
      (if-let [session-id (auth/current-session-id request)]
        (let [{{job-id     :resource-id
                job-status :status} :body} (job/create-job id (str job-type "_nuvlabox_log")
                                             (-> nb-acl
                                               (a/acl-append :edit-data nb-id)
                                               (a/acl-append :manage nb-id))
                                             :priority 50
                                             :execution-mode (nb-utils/get-execution-mode nuvlabox))
              job-msg (str "starting " id " with async " job-id)]
          (when (not= job-status 201)
            (throw (r/ex-response (format "unable to create async job to %s log" job-type) 500 id)))
          (r/map-response job-msg 202 id job-id))
        (throw (r/ex-response "current authentication has no session identifier" 500 id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn already-job-exist
  [job-type {{uuid :uuid} :params :as _request}]
  (try
    (let [id               (str resource-type "/" uuid)
          filter           (format "action='%s' and target-resource/href='%s' and %s"
                                   (str job-type "_nuvlabox_log")
                                   id
                                   "(state='QUEUED' or state='RUNNING')")
          entries          (second (crud/query-as-admin
                                     job/resource-type
                                     {:cimi-params {:filter (parser/parse-cimi-filter filter)}}))
          alive-jobs-count (count entries)]
      (when (pos? alive-jobs-count)
        (let [job-id (-> entries first :id)]
          (r/map-response (format "existing async %s for %s" job-id id) 202 id job-id))))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "fetch"]
  [{{uuid :uuid} :params :as request}]
  (let [id       (str resource-type "/" uuid)
        resource (crud/retrieve-by-id-as-admin id)
        nuvlabox (crud/retrieve-by-id-as-admin (:parent resource))
        job-type "fetch"]
    (a/throw-cannot-manage resource request)
    (a/throw-cannot-manage nuvlabox request)
    (if-let [response (already-job-exist job-type request)]
      response
      (create-job job-type nuvlabox request))))


(defmethod job-interface/get-context ["nuvlabox-log" "fetch_nuvlabox_log"]
  [{:keys [target-resource] :as _resource}]
  (let [nuvlabox-log (some-> target-resource :href crud/retrieve-by-id-as-admin)
        nuvlabox     (some-> nuvlabox-log :parent crud/retrieve-by-id-as-admin)]
    (job-interface/get-context->response
      nuvlabox-log
      nuvlabox)))

;;
;; internal crud
;;

(defn create-log
  [nuvlabox-id acl & [{:keys [since lines]}]]
  (let [log-map        (cond-> {:parent  nuvlabox-id
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

(def resource-metadata (gen-md/generate-metadata ::ns ::nl/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nl/schema)
  (md/register resource-metadata))
