(ns com.sixsq.nuvla.server.resources.job
  "
The job resource represents an asynchronous task that will be queued and
processed at a later time. These are typically generated by other resources to
handle potentially long-running tasks. These resources will respond with a 202
(accepted) response containing the identifier of the job.

This resource should not be confused with the callback resource which
represents a task that will be executed only when triggered by an external
request.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.job.interface :as interface]
    [com.sixsq.nuvla.server.resources.job.utils :as utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.job :as job]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type utils/resource-type)


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const latest-version 2)


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-admin"]
                     :bulk-delete ["group/nuvla-admin"]})

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::job/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::job/schema)
  (md/register resource-metadata)
  (utils/create-job-queue))


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::job/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(defn add-impl [{{:keys [priority execution-mode version created-by]
                  :or {priority 999
                       execution-mode "push"
                       version latest-version} :as body} :body :as request}]
  (a/throw-cannot-add collection-acl request)
  (let [id      (u/new-resource-id resource-type)
        zk-path (when (#{"push" "mixed"} execution-mode)
                  (let [zk-path (utils/add-job-to-queue id priority)]
                    (log/infof "Added %s, zookeeper path %s." id zk-path)
                    zk-path))]
    (-> body
        u/strip-service-attrs
        (assoc :resource-type resource-type)
        (assoc :id id)
        (assoc :state utils/state-queued)
        (assoc :execution-mode execution-mode)
        (assoc :version version)
        u/update-timestamps
        (cond-> (nil? created-by) (u/set-created-by request))
        utils/job-cond->addition
        (crud/add-acl request)
        (cond-> zk-path (assoc :tags [zk-path]))
        crud/validate
        db/add)))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(defmethod crud/edit resource-type
  [{{uuid :uuid} :params :as request}]
  (try
    (let [immutable-keys [:target-resource :action]
          job            (-> (str resource-type "/" uuid)
                             crud/retrieve-by-id-as-admin
                             (a/throw-cannot-edit request)
                             utils/throw-cannot-edit-in-final-state
                             (u/delete-attributes request immutable-keys)
                             (u/merge-body request immutable-keys)
                             (u/update-timestamps)
                             (u/set-updated-by request)
                             (utils/job-cond->edition)
                             (crud/validate))
          response       (db/edit job {:refresh false})]
      (when (utils/is-final-state? job)
        (interface/on-done job))
      response)
    (catch Exception e
      (or (ex-data e) (throw e)))))


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


;;
;; provide an action that allows the job to be stoppable.
;;

(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [cancel-op      (u/action-map id utils/action-cancel)
        get-context-op (u/action-map id utils/action-get-context)
        timeout-op     (u/action-map id utils/action-timeout)]
    (cond-> (crud/set-standard-operations resource request)
            (utils/can-cancel? resource request) (update :operations conj cancel-op)
            (utils/can-timeout? resource request) (update :operations conj timeout-op)
            (utils/can-get-context? resource request) (update :operations conj get-context-op))))

(defn create-cancel-children-jobs-job
  [{:keys [acl] parent-job-id :id :as _parent-job} request]
  (utils/create-job parent-job-id "cancel_children_jobs" acl
                    (auth/current-user-id request)
                    :priority 10))

(defn cancel-children-jobs-async [{action :action :as job} request]
  (when (str/starts-with? action "bulk")
    (create-cancel-children-jobs-job job request)))


(defmethod crud/do-action [resource-type utils/action-cancel]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id       (str resource-type "/" uuid)
          response (-> (crud/retrieve-by-id-as-admin id)
                       (utils/throw-cannot-cancel request)
                       (assoc :state utils/state-canceled)
                       (u/update-timestamps)
                       (u/set-updated-by request)
                       (utils/job-cond->edition)
                       (crud/validate)
                       (db/edit {:refresh false}))
          job      (:body response)]
      (cancel-children-jobs-async job (auth/current-active-claim request))
      (log/warn "Canceled job : " id)
      (interface/on-cancel job)
      response)
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/do-action [resource-type utils/action-get-context]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str resource-type "/" uuid)
        crud/retrieve-by-id-as-admin
        (utils/throw-cannot-get-context request)
        (interface/get-context))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type utils/action-timeout]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [response (-> (str resource-type "/" uuid)
                       crud/retrieve-by-id-as-admin
                       (utils/throw-cannot-timeout request)
                       (assoc :state utils/state-canceled)
                       (u/update-timestamps)
                       (u/set-updated-by request)
                       (utils/job-cond->edition)
                       (crud/validate)
                       (db/edit {:refresh false}))]
      (interface/on-timeout (:body response))
      response)
    (catch Exception e
      (or (ex-data e) (throw e)))))
