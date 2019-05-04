(ns sixsq.nuvla.server.resources.nuvlabox-state
  (:require
    [clojure.string :as s]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.nuvlabox-state :as nuvlabox-state]
    [sixsq.nuvla.auth.utils :as auth-utils]
    [clojure.tools.logging :as log]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:add   ["group/nuvla-admin"]
                     :query ["group/nuvla-user"]})


;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn ::nuvlabox-state/nuvlabox-state))
(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))

;;
;; set the resource identifier to "nuvlabox-state/macaddress"
;;

(defmethod crud/new-identifier resource-type
  [{{nuvlabox-href :href} :nuvlabox :as resource} _]
  (let [nuvlabox-uuid (-> nuvlabox-href (s/split #"/") second)
        id (str resource-type "/" nuvlabox-uuid)]
    (assoc resource :id id)))

;;
;; CRUD operations
;;

(def create-state (std-crud/add-fn resource-type collection-acl resource-type))

(defn create-nuvlabox-state [nuvlabox-id nuvlabox-acl]
  (let [nuvlabox-empty-state {:resource-type resource-type
                              :state         "unknown"
                              :nuvlabox      {:href nuvlabox-id}
                              :nextCheck     "1964-08-25T10:00:00Z"
                              :cpu           0
                              :ram           {:capacity 0
                                              :used     0}
                              :disks         {}
                              :usb           []
                              :acl           nuvlabox-acl}
        nuvlabox-state-request {:params      {:resource-name resource-type}
                                :nuvla/authn auth-utils/internal-identity
                                :body        nuvlabox-empty-state}]
    (create-state nuvlabox-state-request)))

(defmethod crud/edit resource-type
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [current (-> (str resource-type "/" uuid)
                      (db/retrieve request)
                      (a/throw-cannot-edit request))
          merged (cond-> (u/update-timestamps (merge current body))
                         (not (:usb body)) (assoc :usb (:usb current))
                         (not (:disks body)) (assoc :disks (:disks current))
                         (not (:ram body)) (assoc :ram (:ram current))
                         (not (:cpu body)) (assoc :cpu (:cpu current)))]
      (-> merged
          (crud/validate)
          (db/edit request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))

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
  (std-crud/initialize resource-type ::nuvlabox-state/nuvlabox-state))
