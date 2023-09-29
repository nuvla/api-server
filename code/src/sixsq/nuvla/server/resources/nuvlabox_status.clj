(ns sixsq.nuvla.server.resources.nuvlabox-status
  "
The `nuvlabox-status` resource represents the instantaneous state of the
associated NuvlaBox. These resources are usually created as a side-effect of a
NuvlaBox activation, although they can be created manually by an administrator.
Versioned subclasses define the attributes for a particular NuvlaBox release.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox.status-utils :as utils]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.kafka-crud :as kafka-crud]
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


(defn get-nuvlabox-status-name-description
  [nuvlabox-id nuvlabox-name]
  {:name        (nb-utils/format-nb-name
                  nuvlabox-name (nb-utils/short-nb-id nuvlabox-id))
   :description (str "NuvlaEdge status of "
                     (nb-utils/format-nb-name nuvlabox-name nuvlabox-id))})

(defn create-nuvlabox-status
  "Utility to facilitate creating a new nuvlabox-status resource from the
   nuvlabox resource. This will create (as an administrator) an unknown
   state based on the given id and acl. The returned value is the standard
   'add' response for the request."
  [schema-version nuvlabox-id nuvlabox-name nuvlabox-acl]
  (let [status-acl (merge
                     (select-keys nuvlabox-acl [:view-acl :view-data :view-meta])
                     {:owners    ["group/nuvla-admin"]
                      :edit-data [nuvlabox-id]})
        body       (merge
                     (get-nuvlabox-status-name-description nuvlabox-id nuvlabox-name)
                     {:resource-type resource-type
                      :parent        nuvlabox-id
                      :version       schema-version
                      :status        "UNKNOWN"
                      :acl           status-acl})
        request    {:params      {:resource-name resource-type}
                    :nuvla/authn auth/internal-identity
                    :body        body}]
    (add-impl request)))


(defn pre-delete-attrs-hook
  [{resources-prev :resources
    online-prev    :online :as resource}
   request]
  (-> (nb-utils/throw-parent-nuvlabox-is-suspended resource)
      (nb-utils/legacy-heartbeat request)
      (cond-> (some? resources-prev)
              (assoc :resources-prev resources-prev)

              (some? online-prev)
              (assoc :online-prev online-prev))))

(def edit-impl (std-crud/edit-fn resource-type
                                 :pre-delete-attrs-hook pre-delete-attrs-hook
                                 ;:pre-validate-hook minimal-update
                                 :immutable-keys [:online
                                                  :online-prev
                                                  :last-heartbeat
                                                  :next-heartbeat]))


(defmethod crud/edit resource-type
  [request]
  (try
    (let [{:keys [body] :as response} (r/throw-response-not-200
                                        (edit-impl request))]
      (utils/denormalize-changes-nuvlabox body)
      (kafka-crud/publish-on-edit resource-type response)
      (remove-blacklisted response))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defn update-nuvlabox-status
  [id nuvlabox-id nuvlabox-name nuvlabox-acl]
  (let [acl (merge
              (select-keys nuvlabox-acl [:view-acl :view-data :view-meta])
              {:owners    ["group/nuvla-admin"]
               :edit-data [nuvlabox-id]})]
    (crud/edit {:params      {:uuid          (u/id->uuid id)
                              :resource-name resource-type}
                :body        (merge
                               (get-nuvlabox-status-name-description nuvlabox-id nuvlabox-name)
                               {:acl acl})
                :nuvla/authn auth/internal-identity})))


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
