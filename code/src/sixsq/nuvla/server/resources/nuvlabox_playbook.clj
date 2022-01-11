(ns sixsq.nuvla.server.resources.nuvlabox-playbook
  "
The nuvlabox-playbook resource holds scripts that, when configured, 
can be executed by the NuvlaBox device, externally and independently of the 
NuvlaBox Engine software
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-playbook :as nb-playbook]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:add   ["group/nuvla-user"]
                     :query ["group/nuvla-user"]})


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-playbook/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))

;;
;; acl
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (if-let [nuvlabox-id (:parent resource)]
    (let [{nuvlabox-acl :acl} (crud/retrieve-by-id-as-admin nuvlabox-id)
          view-acl (:view-acl nuvlabox-acl)
          edit-acl (:edit-acl nuvlabox-acl)]
      (assoc resource
        :acl (cond-> (assoc (or (:acl resource)
                                (a/default-acl (auth/current-authentication request))) :manage [nuvlabox-id])
                     (not-empty view-acl) (assoc :view-acl (into [] (distinct (merge view-acl nuvlabox-id))))
                     (not-empty edit-acl) (assoc :edit-acl edit-acl))))
    (a/add-acl resource request)))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))


(defmethod crud/add resource-type
  [{{:keys [parent] :as body} :body :as request}]
  (some-> parent
          (db/retrieve request)
          (a/throw-cannot-edit request))
  (-> request
      (update-in [:body] dissoc :output)
      (add-impl)))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [{{:keys [output]} :body :as request}]
  (-> request
      (cond-> output (assoc-in [:body :output] (utils/limit-string-size 10000 output)))
      edit-impl))


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
;; Revoke ssh-key action
;;


(defn save-output
  [{:keys [id output] :as nuvlabox-playbook} new-output]
  (if new-output
    (do
      (try
        (let [concat-output (str new-output "\n" output)
              request       {:params      {:uuid          (u/id->uuid id)
                                           :resource-name resource-type}
                             :body        {:output concat-output}
                             :nuvla/authn auth/internal-identity}
              {status :status :as _resp} (crud/edit request)]
          (if (= 200 status)
            (log/info "the output from the nuvlabox playbook " id " has been updated")
            (let [msg (str "cannot update nuvlabox playbook output for " id)]
              (throw (ex-info msg (r/map-response msg 400 "")))))
          (r/map-response "playbook output saved successfully" 200))
        (catch Exception e
          (or (ex-data e) (throw e)))))
    (logu/log-and-throw-400 "The provided playbook execution output is empty")))


(defmethod crud/do-action [resource-type "save-output"]
  [{{uuid :uuid} :params body :body :as request}]
  (try
    (let [id         (str resource-type "/" uuid)
          new-output (if (:encoded-output body)
                       (utils/decode-base64 (:encoded-output body))
                       (:output body))]
      (-> (db/retrieve id request)
          (a/throw-cannot-manage request)
          (save-output new-output)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/set-operations resource-type
  [{:keys [id] :as resource} request]
  (let [save-output-op (u/action-map id :save-output)]
    (cond-> (crud/set-standard-operations resource request)
            (a/can-manage? resource request) (update :operations conj save-output-op))))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-playbook/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::nb-playbook/schema)
  (md/register resource-metadata))

