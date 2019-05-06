(ns sixsq.nuvla.server.resources.nuvlabox-record
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox-state :as nbs]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.spec.nuvlabox-record :as nuvlabox-record]
    [sixsq.nuvla.server.util.log :as logu]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-user"]
                     :add   ["group/nuvla-user"]})


(def ^:const state-new "NEW")


(def ^:const state-activated "ACTIVATED")


(def ^:const state-quarantined "QUARANTINED")


(def ^:const default-refresh-interval 90)


;;
;; multimethods for validation
;;

(defmulti validate-subtype
          "Validates the given nuvlabox-record resource against a specific
           version of the schema."
          :version)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unsupported nuvlabox-record version: " (:version resource)) resource)))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))


;;
;; set the resource identifier to "nuvlabox-record/macaddress"
;;

(defmethod crud/new-identifier resource-type
  [{:keys [mac-address] :as resource} _]
  (assoc resource :id (str resource-type "/" (-> mac-address str/lower-case (str/replace ":" "")))))


(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [refresh-interval]
     :or   {refresh-interval default-refresh-interval}
     :as   body} :body :as request}]

  (let [new-nuvlabox (assoc body :state state-new
                                 :refresh-interval refresh-interval)]

    (add-impl (assoc request :body new-nuvlabox))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


(defmethod crud/delete resource-type
  [{{uuid :uuid} :params :as request}]
  (let [id (str resource-type "/" uuid)]
    (try
      (-> (db/retrieve id request)
          (a/throw-cannot-delete request)
          (db/delete request))
      (catch Exception e
        (or (ex-data e) (throw e))))))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; Activate operation
;;

(defn activate
  [{:keys [id state acl] :as nuvlabox}]
  (if (= state state-new)
    (do
      (log/warn "Activating nuvlabox:" id)
      ;; FIXME: Uses identifier as claim to access nuvlabox-* resources.
      (let [new-acl (update acl :edit-acl (comp vec conj) id)
            nuvlabox-state-id (-> (nbs/create-nuvlabox-state 0 id new-acl) :body :resource-id)
            activated-nuvlabox (-> nuvlabox
                                   (assoc :state state-activated)
                                   (assoc :acl new-acl)
                                   (assoc :info {:href nuvlabox-state-id})
                                   (utils/create-services))]
        activated-nuvlabox))
    (logu/log-and-throw-400 "Activation is not allowed")))


(defmethod crud/do-action [resource-type "activate"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)
          nuvlabox (db/retrieve id request)
          nuvlabox-activated (activate nuvlabox)]
      (db/edit nuvlabox-activated request))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; Quarantine operation
;;

(defn quarantine [{:keys [id state] :as resource}]
  (if (= state state-activated)
    (do
      (log/warn "Changing nuvlabox status to quarantined : " id)
      (assoc resource :state state-quarantined))
    (logu/log-and-throw-400 (str "Bad nuvlabox state " state))))


(defmethod crud/do-action [resource-type "quarantine"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (-> (db/retrieve id request)
            (a/throw-cannot-manage request)
            quarantine
            (db/edit request))
        (catch Exception e
          (or (ex-data e) (throw e)))))))

;;
;; Set operation
;;

(defmethod crud/set-operations resource-type
  [{:keys [id state] :as resource} request]
  (let [href-activate (str id "/activate")
        href-sc (str id "/quarantine")
        activate-op {:rel (:activate c/action-uri) :href href-activate}
        quarantine-op {:rel (:quarantine c/action-uri) :href href-sc}]
    (cond-> (crud/set-standard-operations resource request)
            (= state state-new) (update-in [:operations] conj activate-op)
            (= state state-activated) (update-in [:operations] conj quarantine-op))))

;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::nuvlabox-record/schema))


