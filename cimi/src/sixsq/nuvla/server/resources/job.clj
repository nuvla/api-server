(ns sixsq.nuvla.server.resources.job
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.job.utils :as ju]
    [sixsq.nuvla.server.resources.spec.job :as job]
    [superstring.core :as str]))


(def ^:const resource-name "Job")

(def ^:const resource-tag (keyword (str (str/camel-case resource-name) "s")))

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "JobCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})

;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::job/job)
  (ju/create-job-queue))


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::job/job))
(defmethod crud/validate resource-uri
  [resource]
  (validate-fn resource))

;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl resource request))

;;
;; CRUD operations
;;

(defn add-impl [{{:keys [priority] :or {priority 999} :as body} :body :as request}]
  (a/can-modify? {:acl collection-acl} request)
  (let [id (u/new-resource-id (u/de-camelcase resource-name))
        zookeeper-path (ju/add-job-to-queue id priority)
        new-job (-> body
                    u/strip-service-attrs
                    (assoc :resourceURI resource-uri)
                    (assoc :id id)
                    (assoc :state ju/state-queued)
                    u/update-timestamps
                    ju/job-cond->addition
                    (crud/add-acl request)
                    (assoc :tags [zookeeper-path])
                    (crud/validate))]
    (db/add resource-name new-job {})))

(defmethod crud/add resource-name
  [request]
  (add-impl request))

(def retrieve-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))

(defn edit-impl
  [{{select :select} :cimi-params {uuid :uuid} :params body :body :as request}]
  (try
    (let [current (-> (str (u/de-camelcase resource-name) "/" uuid)
                      (db/retrieve (assoc-in request [:cimi-params :select] nil))
                      (a/can-modify? request))
          dissoc-keys (-> (map keyword select)
                          (set)
                          (u/strip-select-from-mandatory-attrs))
          current-without-selected (apply dissoc current dissoc-keys)
          merged (merge current-without-selected body)]
      (-> merged
          (u/update-timestamps)
          (ju/job-cond->edition)
          (crud/validate)
          (db/edit request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/edit resource-name
  [request]
  (edit-impl request))

(def delete-impl (std-crud/delete-fn resource-name))

(defmethod crud/delete resource-name
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))

(defmethod crud/query resource-name
  [request]
  (query-impl request))

;;
;; provide an action that allows the job to be stoppable.
;;

(defmethod crud/set-operations resource-uri
  [{:keys [id] :as resource} request]
  (let [href (str id "/stop")
        collect-op {:rel (:stop c/action-uri) :href href}]
    (-> (crud/set-standard-operations resource request)
        (update-in [:operations] conj collect-op))))


(defmethod crud/do-action [resource-url "stop"]
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str (u/de-camelcase resource-name) "/" uuid)
        (db/retrieve request)
        (a/can-modify? request)
        (ju/stop)
        (db/edit request))
    (catch Exception e
      (or (ex-data e) (throw e)))))
