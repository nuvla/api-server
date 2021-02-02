(ns sixsq.nuvla.server.resources.subscription
  "
Collection for holding subscriptions.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.subscription :as subs-schema]
    [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user"]
                     :bulk-delete ["group/nuvla-user"]})


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::subs-schema/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::subs-schema/schema)
  (md/register resource-metadata))


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::subs-schema/schema))


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

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [request]
  (let [{:keys [name description]} (:body request)
        name (if (not name)
               resource-type
               name)
        request (assoc-in request [:body :name] name)
        description (if (not description)
                      name
                      description)
        request (assoc-in request [:body :description] description)
        resp (add-impl request)]
    (ka-crud/publish-on-add resource-type resp)
    resp))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (let [resp (edit-impl request)]
    (ka-crud/publish-on-edit resource-type resp)
    resp))


(defn delete-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (let [resource-id   (str resource-type "/" uuid)
          resource (db/retrieve resource-id request)
          delete-response (-> resource
                              (a/throw-cannot-delete request)
                              (db/delete request))]
      (ka-crud/publish-tombstone resource-type (:resource-id resource))
      delete-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def bulk-delete-impl (std-crud/bulk-delete-fn resource-type collection-acl collection-type))
(defmethod crud/bulk-delete resource-type
  [request]
  (bulk-delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


(defn delete-individual-subscriptions
  [resource-id request]
  (let [filter (format "resource-id='%s'" resource-id)
        authn-info (auth/current-authentication request)
        res (crud/query {:cimi-params {:filter (parser/parse-cimi-filter filter)
                                       :select ["id"]}
                         :params      {:resource-name resource-type}
                         :nuvla/authn authn-info})
        resources (-> res :body :resources)]
    (if (> (count resources) 0)
      (doseq [resource resources]
        (let [id (:id resource)
              res (crud/delete {:params      {:uuid          (some-> id (str/split #"/") second)
                                              :resource-name resource-type}
                                :nuvla/authn auth/internal-identity})]
          (if (not= (:status res) 200)
            (log/warn (format "Failed to delete %s when deleting %s" id resource-id))))))))
