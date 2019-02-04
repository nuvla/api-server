(ns com.sixsq.slipstream.ssclj.resources.storage-bucket
  (:require
    [clojure.string :as str]
    [com.sixsq.slipstream.auth.acl :as a]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.spec.storage-bucket :as bucky]))

(def ^:const resource-tag :storageBuckets)

(def ^:const resource-name "StorageBucket")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "StorageBucketCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

;; only authenticated users can view and create credentials
(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})

;;
;; multimethod for ACLs
;;

(defn create-acl [id]
  {:owner {:principal "ADMIN"
           :type      "ROLE"}
   :rules [{:principal id
            :type      "USER"
            :right     "VIEW"}]})

;;
;; set the resource identifier to "storage-bucket/uuid(connector-href, instanceID)"
;;
(defmethod crud/new-identifier resource-name [json resource-name]
  (let [credential-href (-> json :credentials first :href)
        bucketName (:bucketName json)
        id (u/from-data-uuid (str credential-href bucketName))]
    (assoc json :id (str resource-url "/" id))))

(def validate-fn (u/create-spec-validation-fn ::bucky/storage-bucket))
(defmethod crud/validate
  resource-uri
  [resource]
  (validate-fn resource))

(defmethod crud/add-acl resource-uri
  [{:keys [acl] :as resource} request]
  (if acl
    resource
    (let [user-id (:identity (a/current-authentication request))
          run-owner (some-> request
                            (get-in [:body :externalObject :user :href])
                            (str/replace #"^user/" ""))]
      (if run-owner
        (assoc resource :acl (create-acl run-owner))
        (assoc resource :acl (create-acl user-id))))))

;;
;; CRUD operations
;;
(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))
(defmethod crud/add resource-name
  [{{:keys [serviceOffer]} :body :as request}]
  ;;Create a `currency` attribute when it is defined in the linked serviceOffer
  (if (:price:currency serviceOffer)
    (add-impl (assoc-in request [:body :currency] (:price:currency serviceOffer)))
    (add-impl request)))

(def edit-impl (std-crud/edit-fn resource-name))
(defmethod crud/edit resource-name
  [request]
  (edit-impl request))

(def retrieve-impl (std-crud/retrieve-fn resource-name))
(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))

(def delete-impl (std-crud/delete-fn resource-name))
(defmethod crud/delete resource-name
  [request]
  (delete-impl request))

(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))
(defmethod crud/query resource-name
  [request]
  (query-impl request))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::bucky/storage-bucket))
