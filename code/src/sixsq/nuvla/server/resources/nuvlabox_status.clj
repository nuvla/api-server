(ns sixsq.nuvla.server.resources.nuvlabox-status
  "
The nuvlabox-status resource represents the instantaneous state of the
associated NuvlaBox, including available resources and peripherals. These
resources are usually created as a side-effect of a NuvlaBox activation,
although they can be created manually by an administrator.
"
  (:require
    [sixsq.nuvla.auth.utils :as auth-utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
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
  [schema-version nuvlabox-id nuvlabox-owner]
  (let [status-acl              {:owners    ["group/nuvla-admin"]
                                 :view-acl  [nuvlabox-owner]
                                 :edit-data [nuvlabox-id]}
        body                    {:resource-type resource-type
                                 :parent        nuvlabox-id
                                 :version       schema-version
                                 :status        "UNKNOWN"
                                 :acl           status-acl}
        nuvlabox-status-request {:params      {:resource-name resource-type}
                                 :nuvla/authn auth-utils/internal-identity
                                 :body        body}]
    (add-impl nuvlabox-status-request)))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


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

(defn initialize
  []
  (std-crud/initialize resource-type ::nb-status/schema)
  (md/register (gen-md/generate-metadata ::ns ::nb-status/schema)))
