(ns sixsq.nuvla.server.resources.nuvlabox-status
  "
The `nuvlabox-status` resource represents the instantaneous state of the
associated NuvlaBox. These resources are usually created as a side-effect of a
NuvlaBox activation, although they can be created manually by an administrator.
Versioned subclasses define the attributes for a particular NuvlaBox release.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.middleware.cimi-params.impl :as cimi-params-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox.status-utils :as status-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
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
  [{:keys [version] :as resource}]
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


(defmulti pre-edit
          "Allows nuvlabox-status subclasses to perform actions before updating
           the resource. The default action just returns the unmodified request."
          :version)


(defmethod pre-edit :default
  [resource]
  resource)


(defn get-jobs
  [{nb-id :parent :as resource}]
  (->> {:params      {:resource-name "job"}
        :cimi-params {:filter (cimi-params-impl/cimi-filter
                                {:filter (str "execution-mode='pull' and "
                                              "state!='FAILED' and "
                                              "state!='SUCCESS' and state!='STOPPED'")})
                      :select ["id"]
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
    (let [{:keys [acl] :as current} (-> (str resource-type "/" uuid)
                                        (db/retrieve (assoc-in request [:cimi-params :select] nil))
                                        (a/throw-cannot-edit request))
          jobs                     (get-jobs current)
          rights                   (a/extract-rights (auth/current-authentication request) acl)
          dissoc-keys              (-> (map keyword select)
                                       set
                                       u/strip-select-from-mandatory-attrs
                                       (a/editable-keys rights))
          current-without-selected (apply dissoc current dissoc-keys)
          editable-body (select-keys body (-> body keys (a/editable-keys rights)))
          merged (merge current-without-selected editable-body)]
      (-> merged
          (u/update-timestamps)
          (u/set-updated-by request)
          (assoc :jobs jobs)
          (status-utils/set-online request)
          pre-edit
          crud/validate
          (db/edit request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


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
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-status/schema))

(defn initialize
  []
  (std-crud/initialize resource-type ::nb-status/schema)
  (md/register resource-metadata))
