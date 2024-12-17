(ns com.sixsq.nuvla.server.resources.deployment-set.utils
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [com.sixsq.nuvla.auth.utils :as auth]
            [com.sixsq.nuvla.db.filter.parser :as parser]
            [com.sixsq.nuvla.db.impl :as db]
            [com.sixsq.nuvla.server.resources.common.crud :as crud]
            [com.sixsq.nuvla.server.resources.common.state-machine :as sm]
            [com.sixsq.nuvla.server.resources.common.utils :as u]
            [com.sixsq.nuvla.server.resources.deployment-set.operational-status :as op-status]
            [com.sixsq.nuvla.server.resources.module :as m]
            [com.sixsq.nuvla.server.resources.module.utils :as module-utils]
            [com.sixsq.nuvla.server.resources.nuvlabox :as nuvlabox]
            [com.sixsq.nuvla.server.resources.nuvlabox-status :as nuvlabox-status]
            [com.sixsq.nuvla.server.util.response :as r]
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
(def action-check-requirements "check-requirements")
(def action-auto-update "auto-update")

(def actions [crud/action-edit
              crud/action-delete
              action-start
              action-stop
              action-update
              action-cancel
              action-force-delete
              action-check-requirements
              action-plan
              action-operational-status
              action-recompute-fleet
              action-auto-update])


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

(def guard-auto-update-enabled? :auto-update-enabled)

(defn transition-ok
  [to-state]
  {::tk/on action-ok ::tk/to to-state ::tk/guards [sm/guard-is-admin?]})

(defn transition-nok
  [to-state]
  {::tk/on action-nok ::tk/to to-state ::tk/guards [sm/guard-is-admin?]})

(defn transition-cancel
  [to-state]
  {::tk/on action-cancel ::tk/to to-state ::tk/guards [sm/guard-can-manage?]})

(def transition-start {::tk/on action-start ::tk/to state-starting ::tk/guards [sm/guard-can-manage?]})
(def transition-update {::tk/on action-update ::tk/to state-updating ::tk/guards [sm/guard-can-manage?
                                                                                  guard-operational-status-nok?]})
(def transition-stop {::tk/on action-stop ::tk/to state-stopping ::tk/guards [sm/guard-can-manage?]})
(def transition-edit {::tk/on crud/action-edit ::tk/guards [sm/guard-can-edit?]})
(def transition-edit-admin {::tk/on crud/action-edit ::tk/guards [sm/guard-is-admin?]})
(def transition-delete {::tk/on crud/action-delete ::tk/guards [sm/guard-can-delete?]})
(def transition-force-delete {::tk/on action-force-delete ::tk/guards [sm/guard-can-delete?]})
(def transition-check-requirements {::tk/on action-check-requirements ::tk/guards [sm/guard-can-manage?]})
(def transition-plan {::tk/on action-plan ::tk/guards [sm/guard-can-manage?]})
(def transition-operational-status {::tk/on action-operational-status ::tk/guards [sm/guard-can-manage?]})
(def transition-recompute-fleet {::tk/on action-recompute-fleet ::tk/guards [sm/guard-can-edit?
                                                                             guard-fleet-filter-defined?]})
(def transition-auto-update {::tk/on action-auto-update ::tk/guards [sm/guard-is-admin?
                                                                     guard-auto-update-enabled?]})

(defn operational-status-nok?
  [{{:keys [status]} :operational-status :as _resource}]
  (= status operational-status-nok))

(defn operational-status-ok?
  [{{:keys [status]} :operational-status :as _resource}]
  (= status operational-status-ok))

(defn fleet-filter-defined?
  [resource]
  (some? (get-in resource [:applications-sets 0 :overwrites 0 :fleet-filter])))

(defn auto-update-enabled?
  [resource]
  (true? (:auto-update resource)))

(def state-machine
  {::tk/states [{::tk/name        state-new
                 ::tk/transitions [transition-start
                                   transition-edit
                                   transition-check-requirements
                                   transition-plan
                                   transition-operational-status
                                   transition-delete
                                   transition-recompute-fleet]}
                {::tk/name        state-starting
                 ::tk/transitions [(transition-cancel state-partially-started)
                                   (transition-nok state-partially-started)
                                   (transition-ok state-started)
                                   transition-check-requirements
                                   transition-plan
                                   transition-operational-status
                                   transition-edit-admin]}
                {::tk/name        state-started
                 ::tk/transitions [transition-edit
                                   transition-update
                                   transition-stop
                                   transition-check-requirements
                                   transition-plan
                                   transition-operational-status
                                   transition-force-delete
                                   transition-recompute-fleet
                                   transition-auto-update]}
                {::tk/name        state-partially-started
                 ::tk/transitions [transition-edit
                                   transition-update
                                   transition-stop
                                   transition-check-requirements
                                   transition-plan
                                   transition-operational-status
                                   transition-force-delete
                                   transition-recompute-fleet
                                   transition-auto-update]}
                {::tk/name        state-stopping
                 ::tk/transitions [(transition-cancel state-partially-stopped)
                                   (transition-nok state-partially-stopped)
                                   (transition-ok state-stopped)
                                   transition-check-requirements
                                   transition-plan
                                   transition-operational-status
                                   transition-edit-admin]}
                {::tk/name        state-stopped
                 ::tk/transitions [transition-start
                                   transition-edit
                                   transition-check-requirements
                                   transition-plan
                                   transition-operational-status
                                   transition-delete
                                   transition-recompute-fleet]}
                {::tk/name        state-partially-stopped
                 ::tk/transitions [transition-edit
                                   transition-start
                                   transition-stop
                                   transition-check-requirements
                                   transition-plan
                                   transition-operational-status
                                   transition-force-delete
                                   transition-recompute-fleet]}
                {::tk/name        state-updating
                 ::tk/transitions [(transition-cancel state-partially-updated)
                                   (transition-nok state-partially-updated)
                                   (transition-ok state-updated)
                                   transition-check-requirements
                                   transition-plan
                                   transition-operational-status
                                   transition-edit-admin]}
                {::tk/name        state-updated
                 ::tk/transitions [transition-edit
                                   transition-update
                                   transition-stop
                                   transition-check-requirements
                                   transition-plan
                                   transition-operational-status
                                   transition-force-delete
                                   transition-recompute-fleet
                                   transition-auto-update]}
                {::tk/name        state-partially-updated
                 ::tk/transitions [transition-edit
                                   transition-update
                                   transition-stop
                                   transition-check-requirements
                                   transition-plan
                                   transition-operational-status
                                   transition-force-delete
                                   transition-recompute-fleet
                                   transition-auto-update]}]
   ::tk/guard? (fn [{{:keys [resource _request]} ::tk/process
                     guard                       ::tk/guard :as ctx}]
                 (or (sm/guard? ctx)
                     (condp = guard
                       guard-operational-status-nok? (operational-status-nok? resource)
                       guard-fleet-filter-defined? (fleet-filter-defined? resource)
                       guard-auto-update-enabled? (auto-update-enabled? resource)
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

(defn dep-set-changed?
  [next current]
  (not= next (op-status/operational-status-values-set current)))

(defn save-deployment-set
  [next current]
  (if (dep-set-changed? next current)
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

(defn file-to-map
  [files]
  (array-map-to-map files :file-name :file-content))

(defn merge-files
  [files overwrite-files]
  (mapv
    (fn [[k v]] {:file-name k :file-content v})
    (merge (file-to-map files)
           (file-to-map overwrite-files))))

(defn merge-app
  [{:keys [environmental-variables
           files
           version] :as application}
   application-overwrite]
  (let [env        (merge-env
                     environmental-variables
                     (:environmental-variables application-overwrite))
        files      (merge-files
                     files
                     (:files application-overwrite))
        regs-creds (:registries-credentials application-overwrite)]
    (-> application
        (assoc :version (or (:version application-overwrite) version))
        (cond->
          (seq env) (assoc :environmental-variables env)
          (seq files) (assoc :files files)
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
           {application-href :href {:keys [environmental-variables files]} :content
            :as              module} :module} deployments
          :let [env-vars (->> environmental-variables
                              (map #(select-keys % [:name :value]))
                              (filter :value)
                              vec)
                files    (->> files
                              (map #(select-keys % [:file-name :file-content]))
                              (filter :file-content)
                              vec)]]
      {:id          deployment-id
       :app-set     app-set
       :application (cond-> {:id      (module-utils/full-uuid->uuid application-href)
                             :version (module-utils/module-current-version module)}
                            (seq env-vars)
                            (assoc :environmental-variables env-vars)
                            (seq files)
                            (assoc :files files)
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

(defn query-nuvlaboxes-as
  [cimi-filter authn]
  (let [{:keys [body]} (crud/query {:params      {:resource-name nuvlabox/resource-type}
                                    :cimi-params {:filter (parser/parse-cimi-filter cimi-filter)
                                                  :last   10000}
                                    :nuvla/authn authn})]
    (:resources body)))

(defn query-nuvlaboxes
  [cimi-filter request]
  (query-nuvlaboxes-as cimi-filter (auth/current-authentication request)))

(defn get-missing-edges
  [deployment-set request]
  (let [apps-set-overwrites (get-applications-sets deployment-set)
        edges               (vec (mapcat :fleet apps-set-overwrites))
        existing-edges      (->> request
                                 (query-nuvlaboxes (str "id=" edges))
                                 (map :id)
                                 set)]
    (set/difference (set edges) existing-edges)))

(defn check-apps-permissions
  [{:keys [id] :as deployment-set}]
  (let [owner-request     (auth/get-owner-request deployment-set)
        applications-sets (-> deployment-set
                              get-applications-sets-href
                              (crud/get-resource-throw-nok owner-request))
        apps              (->> (get-applications-sets deployment-set)
                               (mapcat merge-apps (module-utils/get-applications-sets applications-sets)))
        retrieved-apps    (map #(-> (crud/retrieve {:params         {:uuid          (str (u/id->uuid (:id %)) "_" (:version %))
                                                                     :resource-name m/resource-type}
                                                    :request-method :get
                                                    :nuvla/authn    (auth/get-owner-authn deployment-set)})
                                    :body)
                               apps)]
    (when (not= (count apps) (count retrieved-apps))
      (throw (r/ex-response (str "All apps must be visible to DG owner : "
                                 (mapv :id apps)
                                 (vec retrieved-apps)) 403 id)))
    retrieved-apps))

(defn minimum-requirements
  [deployment-set applications-sets]
  (->> (get-applications-sets deployment-set)
       (mapcat merge-apps (module-utils/get-applications-sets applications-sets))
       (map #(crud/retrieve {:params         {:uuid          (str (u/id->uuid (:id %)) "_" (:version %))
                                              :resource-name m/resource-type}
                             :request-method :get
                             :nuvla/authn    auth/internal-identity}))
       (reduce (fn [{:keys [architectures min-cpu min-ram min-disk]}
                    {{{module-architectures :architectures
                       module-min-req       :minimum-requirements} :content} :body}]
                 {:architectures (if (some? module-architectures)
                                   (cond-> (set module-architectures)
                                           (some? architectures) (set/intersection architectures))
                                   architectures)
                  :min-cpu       (+ min-cpu (or (:min-cpu module-min-req) 0))
                  :min-ram       (+ min-ram (or (:min-ram module-min-req) 0))
                  :min-disk      (+ min-disk (or (:min-disk module-min-req) 0))})
               {:architectures nil
                :min-cpu       0.0
                :min-ram       0
                :min-disk      0})))

(defn retrieve-edges
  [edge-ids]
  (let [filter-req (str "id=['" (str/join "','" edge-ids) "']")
        options    {:cimi-params {:filter (parser/parse-cimi-filter filter-req)
                                  :select ["id" "name" "nuvlabox-status"]
                                  :last   10000}}]
    (second (crud/query-as-admin nuvlabox/resource-type options))))

(defn retrieve-edges-status
  [edge-status-ids]
  (let [filter-req (str "id=['" (str/join "','" edge-status-ids) "']")
        options    {:cimi-params {:filter (parser/parse-cimi-filter filter-req)
                                  :select ["id" "architecture" "resources"]
                                  :last   10000}}]
    (second (crud/query-as-admin nuvlabox-status/resource-type options))))

(defn available-resources
  [deployment-set]
  (let [apps-set-overwrites (get-applications-sets deployment-set)
        edge-ids            (vec (mapcat :fleet apps-set-overwrites))
        edges               (retrieve-edges edge-ids)
        edges-status-by-id  (group-by :id (retrieve-edges-status (map :nuvlabox-status edges)))]
    (map (fn [{:keys [id name nuvlabox-status]}]
           (let [{:keys [architecture resources]} (first (get edges-status-by-id nuvlabox-status))
                 {{cpu-capacity :capacity} :cpu
                  {ram-capacity :capacity} :ram
                  disks                    :disks} resources
                 max-disk-av (apply max (cons 0 (map #(- (or (:capacity %) 0) (or (:used %) 0)) disks)))]
             {:edge-id      id
              :edge-name    name
              :architecture architecture
              :cpu          (or cpu-capacity 0)
              :ram          (or ram-capacity 0)
              :disk         max-disk-av}))
         edges)))

(defn unmet-requirements
  [av-resources {:keys [architectures min-cpu min-ram min-disk] :as _min-req}]
  (for [{:keys [edge-id edge-name architecture cpu ram disk]} av-resources
        :let [unmet (cond-> {}
                            (and architecture (some? architectures) (not ((set architectures) architecture)))
                            (assoc :architecture {:supported         architectures
                                                  :edge-architecture architecture})

                            (and cpu min-cpu (< cpu min-cpu))
                            (assoc :cpu {:min min-cpu, :available cpu})

                            (and ram min-ram (< ram min-ram))
                            (assoc :ram {:min min-ram, :available ram})

                            (and disk min-disk (< disk min-disk))
                            (assoc :disk {:min min-disk, :available disk}))]
        :when (seq unmet)]
    (merge {:edge-id   edge-id
            :edge-name edge-name}
           unmet)))

(defn check-requirements
  [deployment-set applications-sets]
  (let [min-req      (minimum-requirements deployment-set applications-sets)
        av-resources (available-resources deployment-set)
        unmet-req    (unmet-requirements av-resources min-req)]
    {:minimum-requirements min-req
     :unmet-requirements   {:n-edges          (count unmet-req)
                            :first-mismatches (take 10 unmet-req)}}))
