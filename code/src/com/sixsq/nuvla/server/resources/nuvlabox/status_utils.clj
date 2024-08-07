(ns com.sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [com.sixsq.nuvla.server.util.time :as time]))

(def DENORMALIZED_FIELD [:online :inferred-location :nuvlabox-engine-version])

(defn status-fields-to-denormalize
  [nuvlabox-status]
  (->> DENORMALIZED_FIELD
       (select-keys nuvlabox-status)
       (filter (comp some? second))
       (into {})))

(defn denormalize-changes-nuvlabox
  [{:keys [parent] :as nuvlabox-status}]
  (let [propagate-status (status-fields-to-denormalize nuvlabox-status)]
    (when (seq propagate-status)
      (db/scripted-edit parent {:body    {:doc propagate-status}
                                :refresh false}))))

(defn status-telemetry-attributes
  [nuvlabox-status
   {:keys [refresh-interval]
    :or   {refresh-interval nb-utils/default-refresh-interval}
    :as   _nuvlabox}]
  (assoc nuvlabox-status
    :last-telemetry (time/now-str)
    :next-telemetry (nb-utils/compute-next-report refresh-interval #(+ % 30))))

(defn build-nuvlabox-status-acl
  [{:keys [id acl] :as _nuvlabox}]
  (merge
    (select-keys acl [:view-acl :view-data :view-meta])
    {:owners    ["group/nuvla-admin"]
     :edit-data [id]}))

(defn set-name-description-acl
  [nuvlabox-status {:keys [id name] :as nuvlabox}]
  (assoc nuvlabox-status
    :name (nb-utils/format-nb-name
            name (nb-utils/short-nb-id id))
    :description (str "NuvlaEdge status of " (nb-utils/format-nb-name name id))
    :acl (build-nuvlabox-status-acl nuvlabox)))

(defn special-body-nuvlabox
  [{{:keys [parent]} :body :as response} request]
  (if (nb-utils/nuvlabox-request? request)
    (assoc response :body {:jobs (nb-utils/get-jobs parent)})
    response))

(defn detect-swarm
  [{{:keys [parent orchestrator node-id cluster-node-role cluster-managers swarm-node-id] :as _nuvlabox-status} :body :as _response}]
  (let [{:keys [infrastructure-service-group] :as _nuvlabox} (db/retrieve parent)
        swarm-node-id-set? (not (str/blank? swarm-node-id))
        attributes         {:swarm-enabled (or (= "swarm" orchestrator)
                                               swarm-node-id-set?)
                            :swarm-manager (or (= "manager" cluster-node-role)
                                               (contains? (set cluster-managers) node-id)
                                               swarm-node-id-set?)}]

    (log/debugf "detect-swarm - parent: %s - isg: %s - attrs: %s - swarm-node-id: %s - orchestrator: %s - node-id: %s - cluster-node-role: %s - cluster-managers: %s"
                parent infrastructure-service-group attributes swarm-node-id orchestrator node-id cluster-node-role cluster-managers)
    (when-let [resource-id (nb-utils/get-service "swarm" infrastructure-service-group)]
      (let [response (db/scripted-edit resource-id {:refresh false :body {:doc attributes}})]
        (log/debugf "detect-swarm - parent: %s - resource-id: %s - scripted-edit: %s" parent resource-id response)))))
