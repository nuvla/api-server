(ns sixsq.nuvla.server.resources.voucher
  "
This resource contains the structure for a voucher, which is to be issued by a
third party and consumed by a Nuvla user.

New vouchers will by default be inserted into the system with state set to
NEW. Then based on the ACLs of that voucher, whoever can view it, can request
it through the activation operation, which will edit the voucher's state to
ACTIVATED, and assign it to the requesting user.

Afterwards, this voucher can also be redeemed through the operation 'redeem',
which adds a new timestamp to the voucher resource for accounting purposes.

Finally, at any time, the owner or user of the voucher can terminate the
voucher via the 'expire' operation.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.voucher :as voucher]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as time]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


;;
;; initialization: common schema for all user creation methods
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::voucher/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::voucher/schema)
  (md/register resource-metadata))


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
  [voucher]
  (if (= (:state voucher) "NEW")
    (do
      (let [activated-timestamp (time/now-str)
            activated-voucher   (assoc voucher :state "ACTIVATED"
                                               :activated activated-timestamp)]
        activated-voucher))
    (throw (r/ex-response "activation is not allowed for this voucher" 400 (:id voucher)))))


(defmethod crud/do-action [resource-type "activate"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id      (str resource-type "/" uuid)
          user-id (:user-id (auth/current-authentication request))
          voucher (db/retrieve id request)
          new-acl (update (:acl voucher) :manage conj user-id)]
      (try
        (-> id
            (db/retrieve request)
            (a/throw-cannot-view-data request)
            activate
            (assoc :user user-id :acl new-acl)
            (db/edit request))
        (catch Exception ei
          (ex-data ei))))
    (catch Exception ei
      (ex-data ei))))


;;
;; Redeem operation
;;

(defn redeem
  [voucher]
  (if (= (:state voucher) "ACTIVATED")
    (do
      (let [redeemed-timestamp (time/now-str)
            redeemed-voucher   (assoc voucher :state "REDEEMED"
                                              :redeemed redeemed-timestamp)]
        redeemed-voucher))
    (throw (r/ex-response "redeem is not allowed for this voucher" 400 (:id voucher)))))


(defmethod crud/do-action [resource-type "redeem"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (-> id
            (db/retrieve request)
            (a/throw-cannot-manage request)
            redeem
            (db/edit request))
        (catch Exception ei
          (ex-data ei))))
    (catch Exception ei
      (ex-data ei))))


;;;
;;; Expire operation
;;;


(defn expire
  [voucher]
  (if (not (= (:state voucher) "EXPIRED"))
    (do
      (let [expired-voucher (assoc voucher :state "EXPIRED")]
        expired-voucher))
    (throw (r/ex-response "voucher is already expired" 400 (:id voucher)))))


(defmethod crud/do-action [resource-type "expire"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (-> id
            (db/retrieve request)
            (a/throw-cannot-manage request)
            expire
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
  (let [activate-op (u/action-map id :activate)
        expire-op   (u/action-map id :expire)
        redeem-op   (u/action-map id :redeem)
        can-manage? (a/can-manage? resource request)
        can-view?   (a/can-view? resource request)]
    (cond-> (crud/set-standard-operations resource request)
            (and can-manage? (#{"ACTIVATED"} state)) (update :operations conj redeem-op)
            (and can-manage? (#{"NEW" "ACTIVATED" "REDEEMED"} state)) (update :operations conj expire-op)
            (and can-view? (#{"NEW"} state)) (update :operations conj activate-op))))
