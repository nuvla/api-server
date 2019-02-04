(ns com.sixsq.slipstream.ssclj.resources.virtual-machine-mapping
  (:require
    [com.sixsq.slipstream.auth.acl :as a]

    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.spec.virtual-machine-mapping :as vmm]))

(def ^:const resource-tag :virtualMachineMappings)

(def ^:const resource-name "VirtualMachineMapping")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "VirtualMachineMappingCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

;; only the administrator can create and view these resources
(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "ADMIN"
                            :type      "ROLE"
                            :right     "ALL"}]})


;;
;; multimethod for ACLs
;;
(defn create-acl
  [id]
  {:owner {:principal "ADMIN"
           :type      "ROLE"}
   :rules [{:principal "ADMIN"
            :type      "USER"
            :right     "VIEW"}]})


;; unconditionally set the ACL to allow only the administrator access
(defmethod crud/add-acl resource-uri
  [{:keys [acl] :as resource} request]
  (assoc resource :acl resource-acl))


;;
;; resource validation
;;
(def validate-fn (u/create-spec-validation-fn ::vmm/virtual-machine-mapping))
(defmethod crud/validate
  resource-uri
  [resource]
  (validate-fn resource))


;;
;; identifier is always cloud-instanceID to ensure uniqueness
;;
(defmethod crud/new-identifier
  resource-name
  [{:keys [cloud instanceID] :as resource} _]
  (assoc resource :id (str resource-url "/" cloud "-" instanceID)))


;;
;; remove the edit operation from resource
;;
(defmethod crud/set-operations resource-uri
  [resource request]
  (try
    (a/can-modify? resource request)
    (let [ops [{:rel (:delete c/action-uri) :href resource-url}]]
      (assoc resource :operations ops))
    (catch Exception e
      (dissoc resource :operations))))


;;
;; CRUD operations
;;
(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))
(defmethod crud/add resource-name
  [request]
  (add-impl request))


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
  (std-crud/initialize resource-url ::vmm/virtual-machine-mapping))
