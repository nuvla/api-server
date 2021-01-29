(ns sixsq.nuvla.server.resources.notification-method
  "
Collection for holding notification method configurations.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.notification-method :as notif-method-schema]
    [sixsq.nuvla.server.util.kafka :as ka]
    [sixsq.nuvla.server.util.kafka-crud :as ka-crud]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as ru]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::notif-method-schema/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::notif-method-schema/schema)
  (md/register resource-metadata))


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::notif-method-schema/schema))


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
  (let [resp (add-impl request)]
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


(defn throw-references-exist
  [resource-id collections request]
  (doseq [collection collections]
    (let [filter (format "method-id='%s'" resource-id)
          authn-info (auth/current-authentication request)
          req {:cimi-params {:filter (parser/parse-cimi-filter filter)
                             :last   0}
               :params      {:resource-name collection}
               :nuvla/authn authn-info}
          count (-> (crud/query req)
                    :body
                    :count)]
      (if (> count 0)
        (throw (ru/ex-conflict (format "References to %s exist in %s." resource-id collection)))))))

(defn integrity-check
  [resource-id collections request]
  (throw-references-exist resource-id collections request)
  resource-id)

(def collections ["subscription"
                  "subscription-config"])

(defn delete-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (let [resource-id (str resource-type "/" uuid)
          delete-response (-> resource-id
                              (integrity-check collections request)
                              (db/retrieve request)
                              (a/throw-cannot-delete request)
                              (db/delete request))]
      (ka-crud/publish-tombstone resource-type resource-id)
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

