(ns sixsq.nuvla.server.resources.voucher
  "
This resource contains the structure for a voucher, which
is to be issued by a third party and used by any Nuvla user.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as sc]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.voucher :as voucher]
    [sixsq.nuvla.server.util.response :as r]
    [clj-time.core :as time]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [clojure.pprint :as pp]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; initialization: common schema for all user creation methods
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::voucher/schema)
  (md/register (gen-md/generate-metadata ::ns ::voucher/schema)))


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::voucher/schema))


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
  (add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))



;;
;; Activate operation
;;

(defn activate
  [{:keys [id state] :as voucher}]
  (throw (r/ex-response voucher 400 "id"))
  (if (= state "new")
    (do
      (let [activated-timestamp (u/unparse-timestamp-datetime (time/now))
            activated-voucher (-> voucher
                                   (assoc :state "activated")
                                   (assoc :activated activated-timestamp))]
        [activated-voucher]))
    (throw (r/ex-response "activation is not allowed for this voucher" 400 id))))


(defmethod crud/do-action [resource-type "activate"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (-> id
            (db/retrieve request)
            (a/can-edit-acl? request)
            (pp/pprint )
            ;:body
            activate
            (db/edit request))
        (catch Exception ei
          (ex-data ei))))
    (catch Exception ei
      (ex-data ei))))



;;
;; Set operation
;;

(defmethod crud/set-operations resource-type
  [{:keys [id state] :as resource} request]
  (let [href-activate (str id "/activate")
        activate-op {:rel (:activate sc/action-uri) :href href-activate}]
    (cond-> (crud/set-standard-operations resource request)
            (= state "new") (update-in [:operations] conj activate-op)
            )))