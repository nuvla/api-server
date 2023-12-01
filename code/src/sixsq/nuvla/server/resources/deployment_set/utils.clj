(ns sixsq.nuvla.server.resources.deployment-set.utils
  (:require [clojure.string :as str]
            [sixsq.nuvla.db.filter.parser :as parser]
            [sixsq.nuvla.db.impl :as db]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.state-machine :as sm]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.resources.module.utils :as module-utils]
            [sixsq.nuvla.server.resources.nuvlabox :as nuvlabox]
            [tilakone.core :as tk]))

(def action-start "start")
(def action-stop "stop")
(def action-update "update")
(def action-cancel "cancel")
(def action-ok "ok")
(def action-nok "nok")
(def action-force-delete "force-delete")
(def action-plan "plan")
(def action-operational-status "operational-status")
(def action-recompute-fleet "recompute-fleet")

(def actions [crud/action-edit
              crud/action-delete
              action-start
              action-stop
              action-update
              action-cancel
              action-force-delete
              action-plan
              action-operational-status
              action-recompute-fleet])


(def state-new "NEW")
(def state-starting "STARTING")
(def state-started "STARTED")
(def state-stopping "STOPPING")
(def state-stopped "STOPPED")
(def state-partially-started "PARTIALLY-STARTED")
(def state-partially-updated "PARTIALLY-UPDATED")
(def state-partially-stopped "PARTIALLY-STOPPED")
(def state-updating "UPDATING")
(def state-updated "UPDATED")

(def states [state-new
             state-starting
             state-started
             state-stopping
             state-stopped
             state-partially-started
             state-partially-updated
             state-partially-stopped
             state-updating
             state-updated])

(def operational-status-ok "OK")
(def operational-status-nok "NOK")

(def operational-statuses [operational-status-ok
                           operational-status-nok])

(def guard-operational-status-nok? :operational-status-nok)

(def guard-fleet-filter-defined? :fleet-filter-defined)

(defn transition-ok
  [to-state]
  {::tk/on action-ok ::tk/to to-state ::tk/guards [sm/guard-is-admin?]})

(defn transition-nok
  [to-state]
  {::tk/on action-nok ::tk/to to-state ::tk/guards [sm/guard-is-admin?]})

(defn transition-cancel
  [to-state]
  {::tk/on action-cancel ::tk/to to-state ::tk/guards [sm/guard-can-manage?]})

(def transition-start {::tk/on action-start ::tk/to state-starting ::tk/guards [sm/guard-can-manage?
                                                                                guard-operational-status-nok?]})
(def transition-update {::tk/on action-update ::tk/to state-updating ::tk/guards [sm/guard-can-manage?
                                                                                  guard-operational-status-nok?]})
(def transition-stop {::tk/on action-stop ::tk/to state-stopping ::tk/guards [sm/guard-can-manage?]})
(def transition-edit {::tk/on crud/action-edit ::tk/guards [sm/guard-can-edit?]})
(def transition-delete {::tk/on crud/action-delete ::tk/guards [sm/guard-can-delete?]})
(def transition-force-delete {::tk/on action-force-delete ::tk/guards [sm/guard-can-delete?]})
(def transition-plan {::tk/on action-plan ::tk/guards [sm/guard-can-manage?]})
(def transition-operational-status {::tk/on action-operational-status ::tk/guards [sm/guard-can-manage?]})
(def transition-recompute-fleet {::tk/on action-recompute-fleet ::tk/guards [sm/guard-can-edit?
                                                                             guard-fleet-filter-defined?]})

(defn operational-status-nok?
  [{{:keys [status]} :operational-status :as _resource}]
  (= status operational-status-nok))

(defn fleet-filter-defined?
  [resource]
  (some? (get-in resource [:applications-sets 0 :overwrites 0 :fleet-filter])))

(def state-machine
  {::tk/states [{::tk/name        state-new
                 ::tk/transitions [transition-start
                                   transition-edit
                                   transition-plan
                                   transition-operational-status
                                   transition-delete
                                   transition-recompute-fleet]}
                {::tk/name        state-starting
                 ::tk/transitions [(transition-cancel state-partially-started)
                                   (transition-nok state-partially-started)
                                   (transition-ok state-started)
                                   transition-plan
                                   transition-operational-status]}
                {::tk/name        state-started
                 ::tk/transitions [transition-edit
                                   transition-update
                                   transition-stop
                                   transition-plan
                                   transition-operational-status
                                   transition-force-delete
                                   transition-recompute-fleet]}
                {::tk/name        state-partially-started
                 ::tk/transitions [transition-edit
                                   transition-update
                                   transition-stop
                                   transition-plan
                                   transition-operational-status
                                   transition-force-delete
                                   transition-recompute-fleet]}
                {::tk/name        state-stopping
                 ::tk/transitions [(transition-cancel state-partially-stopped)
                                   (transition-nok state-partially-stopped)
                                   (transition-ok state-stopped)
                                   transition-plan
                                   transition-operational-status]}
                {::tk/name        state-stopped
                 ::tk/transitions [transition-start
                                   transition-edit
                                   transition-plan
                                   transition-operational-status
                                   transition-delete
                                   transition-recompute-fleet]}
                {::tk/name        state-partially-stopped
                 ::tk/transitions [transition-edit
                                   transition-start
                                   transition-stop
                                   transition-plan
                                   transition-operational-status
                                   transition-force-delete
                                   transition-recompute-fleet]}
                {::tk/name        state-updating
                 ::tk/transitions [(transition-cancel state-partially-updated)
                                   (transition-nok state-partially-updated)
                                   (transition-ok state-updated)
                                   transition-plan
                                   transition-operational-status]}
                {::tk/name        state-updated
                 ::tk/transitions [transition-edit
                                   transition-update
                                   transition-stop
                                   transition-plan
                                   transition-operational-status
                                   transition-force-delete
                                   transition-recompute-fleet]}
                {::tk/name        state-partially-updated
                 ::tk/transitions [transition-edit
                                   transition-update
                                   transition-stop
                                   transition-plan
                                   transition-operational-status
                                   transition-force-delete
                                   transition-recompute-fleet]}]
   ::tk/guard? (fn [{{:keys [resource _request]} ::tk/process
                     guard                       ::tk/guard :as ctx}]
                 (or (sm/guard? ctx)
                     (condp = guard
                       guard-operational-status-nok? (operational-status-nok? resource)
                       guard-fleet-filter-defined? (fleet-filter-defined? resource)
                       false)))
   ::tk/state  state-new})

(defn get-operations
  [{:keys [id] :as resource} request]
  (->> actions
       (map (fn [action]
              (when (sm/can-do-action? action resource request)
                (if (#{crud/action-delete crud/action-edit} action)
                  (u/operation-map id action)
                  (u/action-map id action)))))
       (remove nil?)))

(defn action-job-name
  [action]
  (str "deployment_set_" (str/replace action #"-" "_")))

(defn bulk-action-job-name
  [action]
  (str "bulk_" (action-job-name action)))

(defn save-deployment-set
  [next current]
  (if (not= next current)
    (-> next
        (u/update-timestamps)
        (crud/validate)
        db/edit
        :body)
    current))

(defn get-first-applications-sets
  [deployment-set]
  (get-in deployment-set [:applications-sets 0] {}))

(defn get-applications-sets
  [deployment-set]
  (-> deployment-set
      get-first-applications-sets
      (get :overwrites [])))

(defn get-applications-sets-href
  [deployment-set]
  (->> deployment-set
       get-first-applications-sets
       ((juxt :id :version))
       (str/join "_")))

(defn app-set-name
  [app-set]
  (:name app-set))

(defn app-set-targets
  [app-set]
  (or (get app-set :fleet)
      (get app-set :targets [])))

(defn app-set-applications
  [app-set]
  (get app-set :applications []))

(defn array-map-to-map
  [m k-fn v-fn]
  (->> m
       (map (juxt k-fn v-fn))
       (into {})))

(defn env-to-map
  [environmental-variables]
  (array-map-to-map environmental-variables :name :value))

(defn merge-env
  [environmental-variables overwrite-environmental-variables]
  (mapv
    (fn [[k v]] {:name k :value v})
    (merge (env-to-map environmental-variables)
           (env-to-map overwrite-environmental-variables))))

(defn merge-app
  [{:keys [environmental-variables
           version] :as application}
   application-overwrite]
  (let [env        (merge-env
                     environmental-variables
                     (:environmental-variables application-overwrite))
        regs-creds (:registries-credentials application-overwrite)]
    (-> application
        (assoc :version (or (:version application-overwrite) version))
        (cond->
          (seq env) (assoc :environmental-variables env)
          (seq regs-creds) (assoc :registries-credentials regs-creds)))))

(defn merge-apps
  [app-set app-set-overwrite]
  (let [apps-overwrites (-> app-set-overwrite
                            app-set-applications
                            (array-map-to-map :id identity))]
    (map
      #(merge-app % (get apps-overwrites (:id %)))
      (app-set-applications app-set))))

(defn plan-set
  [application-set application-set-overwrites]
  (for [t (app-set-targets application-set-overwrites)
        a (merge-apps application-set application-set-overwrites)]
    {:target      t
     :application a
     :app-set     (app-set-name application-set)}))

(defn plan
  [deployment-set applications-sets]
  (set
    (mapcat plan-set
            (module-utils/get-applications-sets applications-sets)
            (get-applications-sets deployment-set))))

(defn current-deployments
  [deployment-set-id]
  (let [filter-req (str "deployment-set='" deployment-set-id "'")
        options    {:cimi-params {:filter (parser/parse-cimi-filter filter-req)
                                  :select ["id" "module" "nuvlabox" "parent" "state" "app-set"]
                                  :last   10000}}]
    (second (crud/query-as-admin "deployment" options))))


(defn current-state
  [{:keys [id] :as _deployment-set}]
  (let [deployments (current-deployments id)]
    (for [{:keys                     [nuvlabox parent state app-set registries-credentials] deployment-id :id
           {application-href :href {:keys [environmental-variables]} :content
            :as              module} :module} deployments
          :let [env-vars (->> environmental-variables
                              (map #(select-keys % [:name :value]))
                              (filter :value))]]
      {:id          deployment-id
       :app-set     app-set
       :application (cond-> {:id      (module-utils/full-uuid->uuid application-href)
                             :version (module-utils/module-current-version module)}
                            (seq env-vars)
                            (assoc :environmental-variables env-vars)
                            (seq registries-credentials)
                            (assoc :registries-credentials registries-credentials))
       :target      (or nuvlabox parent)
       :state       state})))

(defn operational-status-dependent-action
  [resource _request]
  (if (operational-status-nok? resource)
    action-nok
    action-ok))

(defn deployments-dependent-action
  [{deployment-set-id :id} _request]
  (if (every? (comp #(= "STOPPED" %) :state) (current-deployments deployment-set-id))
    action-ok
    action-nok))

(defn query-nuvlaboxes
  [cimi-filter request]
  (let [{:keys [body]} (crud/query {:params      {:resource-name nuvlabox/resource-type}
                                    :cimi-params {:filter (parser/parse-cimi-filter cimi-filter)
                                                  :last   10000}
                                    :nuvla/authn (:nuvla/authn request)})]
    (:resources body)))

