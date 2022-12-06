(ns sixsq.nuvla.server.resources.nuvlabox-status
  "
The `nuvlabox-status` resource represents the instantaneous state of the
associated NuvlaBox. These resources are usually created as a side-effect of a
NuvlaBox activation, although they can be created manually by an administrator.
Versioned subclasses define the attributes for a particular NuvlaBox release.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox.status-utils :as status-utils]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:add   ["group/nuvla-admin"]
                     :query ["group/nuvla-user"]})


;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given nuvlabox-status resource against a specific
           version of the schema."
          :version)


(defmethod validate-subtype :default
  [{:keys [version] :as _resource}]
  (if version
    (throw (r/ex-bad-request (str "unsupported nuvlabox-status version: " version)))
    (throw (r/ex-bad-request "missing nuvlabox-status version"))))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (add-impl request))


(def blacklist-response-keys [:resources-prev
                              :online-prev])


(defn remove-blacklisted
  [response]
  (if (= collection-type (get-in response [:body :resource-type]))
    (update-in response [:body :resources]
               #(for [r %] (apply dissoc r blacklist-response-keys)))
    (update response :body #(apply dissoc % blacklist-response-keys))))


(defn create-nuvlabox-status
  "Utility to facilitate creating a new nuvlabox-status resource from the
   nuvlabox resource. This will create (as an administrator) an unknown
   state based on the given id and acl. The returned value is the standard
   'add' response for the request."
  [schema-version nuvlabox-id nuvlabox-acl]
  (let [status-acl (merge
                     (select-keys nuvlabox-acl [:view-acl :view-data :view-meta])
                     {:owners    ["group/nuvla-admin"]
                      :edit-data [nuvlabox-id]})
        body       {:resource-type resource-type
                    :parent        nuvlabox-id
                    :version       schema-version
                    :status        "UNKNOWN"
                    :acl           status-acl}
        request    {:params      {:resource-name resource-type}
                    :nuvla/authn auth/internal-identity
                    :body        body}]
    (add-impl request)))


(defn get-jobs
  [{nb-id :parent :as _resource}]
  (->> {:params      {:resource-name "job"}
        :cimi-params {:filter  (cimi-params-impl/cimi-filter
                                 {:filter (str "execution-mode='pull' and "
                                               "state!='FAILED' and "
                                               "state!='SUCCESS' and state!='STOPPED'")})
                      :select  ["id"]
                      :orderby [["created" :asc]]}
        :nuvla/authn {:user-id      nb-id
                      :active-claim nb-id
                      :claims       #{nb-id "group/nuvla-user" "group/nuvla-anon"}}}
       crud/query
       :body
       :resources
       (mapv :id)))

(defn edit-impl [{{select :select} :cimi-params {uuid :uuid} :params body :body :as request}]
  (try
    (let [{:keys [parent acl] :as current} (-> (str resource-type "/" uuid)
                                               (db/retrieve (assoc-in request [:cimi-params :select] nil))
                                               (a/throw-cannot-edit request)
                                               (utils/throw-parent-nuvlabox-is-suspended))

          jobs                     (get-jobs current)
          rights                   (a/extract-rights (auth/current-authentication request) acl)
          dissoc-keys              (-> (map keyword select)
                                       set
                                       u/strip-select-from-mandatory-attrs
                                       (a/editable-keys rights))
          current-without-selected (apply dissoc current dissoc-keys)
          editable-body            (select-keys body (-> body keys (a/editable-keys rights)))
          is-nuvlabox?             (-> (auth/current-active-claim request)
                                       (str/starts-with? "nuvlabox/"))
          online                   (or is-nuvlabox? (:online body))
          online-prev              (:online current)
          edit-fn                  #(let [response (db/edit %1 request)]
                                      (status-utils/denormalize-changes-nuvlabox %)
                                      response)
          minimal-update           #(-> %
                                        (u/update-timestamps)
                                        (u/set-updated-by request)
                                        (cond-> (some? online) (assoc :online online))
                                        (cond-> online (assoc :next-heartbeat (status-utils/get-next-heartbeat parent)))
                                        (cond-> (some? online-prev) (assoc :online-prev online-prev)))
          new-status               (-> current-without-selected
                                       (merge editable-body)
                                       (minimal-update)
                                       (assoc :jobs jobs)
                                       (cond-> (contains? body :resources) (assoc :resources-prev (:resources current))))
          spec-exception           (try
                                     (crud/validate new-status)
                                     nil
                                     (catch Exception e
                                       e))]
      (if spec-exception
        (do
          (when is-nuvlabox?
            ;; update heartbeat only when spec issue
            (-> current
                (minimal-update)
                (crud/validate)
                (edit-fn))
            (log/errorf "Nuvlabox got a spec issue while sending telemetry: %s" parent))
          (throw spec-exception))
        (edit-fn new-status)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/edit resource-type
  [request]
  (-> (edit-impl request)
      remove-blacklisted))


(defn update-nuvlabox-status
  [id nuvlabox-id nuvlabox-acl]
  (let [acl     (merge
                  (select-keys nuvlabox-acl [:view-acl :view-data :view-meta])
                  {:owners    ["group/nuvla-admin"]
                   :edit-data [nuvlabox-id]})
        request {:params      {:uuid          (u/id->uuid id)
                               :resource-name resource-type}
                 :body        {:acl acl}
                 :nuvla/authn auth/internal-identity}]
    (edit-impl request)))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (-> (retrieve-impl request)
      remove-blacklisted))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (-> (query-impl request)
      remove-blacklisted))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-status/schema))

(defn initialize
  []
  (std-crud/initialize resource-type ::nb-status/schema)
  (md/register resource-metadata))
