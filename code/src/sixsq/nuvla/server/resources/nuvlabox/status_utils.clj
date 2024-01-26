(ns sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [sixsq.nuvla.server.resources.ts-nuvlaedge :as ts-nuvlaedge]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.time :as time]))

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
  [{{:keys [parent orchestrator node-id cluster-node-role cluster-managers swarm-node-id]} :body :as _response}
   _request]

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
      (log/debugf "detect-swarm - parent: %s - resource-id: %s - scripted-edit: %s"
                  parent resource-id (db/scripted-edit resource-id {:refresh false
                                                                    :body    {:doc attributes}})))))

(defmulti nuvlabox-status->metric-data (fn [_ metric] metric))

(defmethod nuvlabox-status->metric-data :default
  [{:keys [resources]} metric]
  (when-let [metric-data (get resources metric)]
    [metric-data]))

(defmethod nuvlabox-status->metric-data :cpu
  [{{:keys [cpu]} :resources} _]
  (when cpu
    [(select-keys cpu
                  [:capacity
                   :load
                   :load-1
                   :load-5
                   :context-switches
                   :interrupts
                   :software-interrupts
                   :system-calls])]))

(defmethod nuvlabox-status->metric-data :ram
  [{{:keys [ram]} :resources} _]
  (when ram
    [(select-keys ram [:capacity :used])]))

(defmethod nuvlabox-status->metric-data :disk
  [{{:keys [disks]} :resources} _]
  (when (seq disks)
    (mapv #(select-keys % [:device :capacity :used]) disks)))

(defmethod nuvlabox-status->metric-data :network
  [{{:keys [net-stats] :as resources} :resources} _]
  (when (seq net-stats)
    (mapv #(select-keys % [:interface :bytes-transmitted :bytes-received]) net-stats)))

(defmethod nuvlabox-status->metric-data :power-consumption
  [{{:keys [power-consumption]} :resources} _]
  (when (seq power-consumption)
    (mapv #(select-keys % [:metric-name :energy-consumption :unit]) power-consumption)))

(defn nuvlabox-status->ts-bulk-insert-request-body
  [{:keys [parent current-time] :as nuvlabox-status}]
  (when current-time
    (->> [:cpu :ram :disk :network :power-consumption]
         (map (fn [metric]
                (->> (nuvlabox-status->metric-data nuvlabox-status metric)
                     (map #(assoc {:nuvlaedge-id parent
                                   :metric       (name metric)
                                   :timestamp    current-time}
                             metric %)))))
         (apply concat))))

(defn nuvlabox-status->ts-bulk-insert-request
  [response]
  (let [body (nuvlabox-status->ts-bulk-insert-request-body (:body response))]
    (when (seq body)
      {:headers     {"bulk" true}
       :params      {:resource-name ts-nuvlaedge/resource-type
                     :action        "bulk-insert"}
       :body        body
       :nuvla/authn auth/internal-identity})))

(defn granularity->duration
  "Converts from a string of the form <n>-<units> to java.time duration"
  [granularity]
  (let [[_ n unit] (re-matches #"(.*)-(.*)" (name granularity))]
    (try
      (time/duration (Integer/parseInt n) (keyword unit))
      (catch Exception _
        (logu/log-and-throw-400 (str "unrecognized value for granularity " granularity))))))

(defn granularity->ts-interval
  "Converts from a string of the form <n>-<units> to an ElasticSearch interval string"
  [granularity]
  (let [[_ n unit] (re-matches #"(.*)-(.*)" (name granularity))]
    (str n (case unit
             "seconds" "s"
             "minutes" "m"
             "hours" "h"
             "days" "d"
             "weeks" "d"
             "months" "M"
             (logu/log-and-throw-400 (str "unrecognized value for granularity " granularity))))))
