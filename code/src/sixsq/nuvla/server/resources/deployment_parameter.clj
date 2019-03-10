(ns sixsq.nuvla.server.resources.deployment-parameter
  (:require
    [clojure.string :as str]

    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment :as d]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.spec.deployment-parameter :as deployment-parameter]
    [sixsq.nuvla.server.resources.spec.event :as event]
    [sixsq.nuvla.server.utils :as r]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


;; only authenticated users can view and create credentials

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "VIEW"}]})


(defn parameter->uiid
  [deployment-href node-id name]
  (let [id (str/join ":" [deployment-href node-id name])]
    (u/from-data-uuid id)))


(def next-state-machine-transition-map {"Provisioning"   "Executing"
                                        "Executing"      "SendingReports"
                                        "SendingReports" "Ready"
                                        "Ready"          "Ready"
                                        "Done"           "Done"
                                        "Aborted"        "Aborted"
                                        "Cancelled"      "Cancelled"})


(defn next-state
  [current-state]
  (let [next-state (get next-state-machine-transition-map current-state)]
    (if (nil? next-state)
      (throw (r/ex-bad-request (str "complete state invalid: " current-state)))
      next-state)))


(defn is-complete-parameter?
  [name]
  (= name "complete"))


(defn update-state
  [current-state deployment-href]
  (let [new-state (next-state current-state)
        uuid (parameter->uiid deployment-href nil "ss:state")
        content-request {:params   {:resource-name resource-type
                                    :uuid          uuid}
                         :identity std-crud/internal-identity
                         :body     {:value new-state}}
        {:keys [status body] :as response} (-> content-request crud/edit)]
    (when (not= status 200)
      (log/error response)
      (throw (r/ex-response (str "A failure happened during update of deployment state." response) 500)))

    (when (not= current-state new-state)
      (event-utils/create-event deployment-href new-state (:acl body)
                                :severity (if (= new-state "Aborted")
                                            event/severity-critical
                                            event/severity-medium)
                                :type event/type-state))))


;;
;; multimethod for ACLs
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


(def validate-fn (u/create-spec-validation-fn ::deployment-parameter/deployment-parameter))
(defmethod crud/validate resource-type
  [{:keys [name value deployment] :as resource}]
  (let [deployment-href (:href deployment)]
    (case name
      "complete" (some-> value
                         (update-state deployment-href))
      "ss:abort" (when value (update-state "Aborted" deployment-href))
      "ss:state" (let [deployment-request {:params      {:resource-name d/resource-type
                                                         :uuid          (u/document-id deployment-href)}
                                           :cimi-params {:select #{"keepRunning"}}
                                           :identity    std-crud/internal-identity}
                       deployment-data (-> deployment-request crud/retrieve :body)
                       keep-running (get deployment-data :keepRunning "Always")]
                   (when (or (and (= keep-running "Never") (#{"Ready" "Aborted"} value))
                             (and (= keep-running "On Success") (= value "Aborted"))
                             (and (= keep-running "On Error") (= value "Ready")))
                     (crud/do-action {:params   {:action        "stop"
                                                 :resource-name d/resource-type
                                                 :uuid          (u/document-id deployment-href)}
                                      :identity std-crud/internal-identity})))
      nil))
  (validate-fn resource))

;;
;; set the resource identifier to "deployment-parameter/predictable-uuid3-from-string"
;;

(defmethod crud/new-identifier resource-type
  [{:keys [deployment node-id name] :as parameter} resource-name]
  (->> (parameter->uiid (:href deployment) node-id name)
       (str resource-type "/")
       (assoc parameter :id)))

;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [{{:keys [name value deployment acl]} :body :as request}]
  (when (= name "ss:state")
    (event-utils/create-event (:href deployment) value acl
                              :severity event/severity-medium
                              :type event/type-state))
  (add-impl request))


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
  (std-crud/initialize resource-type ::deployment-parameter/deployment-parameter))
