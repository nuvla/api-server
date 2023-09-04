(ns sixsq.nuvla.server.resources.deployment-set.utils
  (:require [clojure.string :as str]
            [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.db.impl :as db]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.state-machine :as sm]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [tilakone.core :as tk]
            [sixsq.nuvla.server.resources.module.utils :as module-utils]))

(def action-start "start")
(def action-stop "stop")
(def action-update "update")
(def action-cancel "cancel")
(def action-ok "ok")
(def action-nok "nok")
(def action-force-delete "force-delete")
(def action-plan "plan")

(def actions [action-start
              action-stop
              action-update
              action-cancel
              action-ok
              action-nok
              action-force-delete
              action-plan])


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

(def state-machine
  {::tk/states [{::tk/name        state-new
                 ::tk/transitions [{::tk/on action-start, ::tk/to state-starting}
                                   {::tk/on crud/action-edit, ::tk/to tk/_}
                                   {::tk/on crud/action-delete, ::tk/to tk/_}]}
                {::tk/name        state-starting
                 ::tk/transitions [{::tk/on action-cancel, ::tk/to state-partially-started}
                                   {::tk/on action-nok, ::tk/to state-partially-started}
                                   {::tk/on action-ok, ::tk/to state-started}]}
                {::tk/name        state-started
                 ::tk/transitions [{::tk/on crud/action-edit, ::tk/to tk/_}
                                   {::tk/on action-update, ::tk/to state-updating}
                                   {::tk/on action-stop, ::tk/to state-stopping}]}
                {::tk/name        state-started
                 ::tk/transitions [{::tk/on crud/action-edit, ::tk/to tk/_}
                                   {::tk/on action-update, ::tk/to state-updating}
                                   {::tk/on action-stop, ::tk/to state-stopping}]}
                {::tk/name        state-stopping
                 ::tk/transitions [{::tk/on action-cancel, ::tk/to state-partially-stopped}
                                   {::tk/on action-nok, ::tk/to state-partially-stopped}
                                   {::tk/on action-ok, ::tk/to state-stopped}]}
                {::tk/name        state-updating
                 ::tk/transitions [{::tk/on action-cancel, ::tk/to state-partially-updated}
                                   {::tk/on action-nok, ::tk/to state-partially-updated}
                                   {::tk/on action-ok, ::tk/to state-updated}]}
                {::tk/name        state-stopped
                 ::tk/transitions [{::tk/on action-start, ::tk/to state-starting}
                                   {::tk/on crud/action-edit, ::tk/to tk/_}
                                   {::tk/on crud/action-delete, ::tk/to tk/_}]}
                {::tk/name        state-updated
                 ::tk/transitions [{::tk/on crud/action-edit, ::tk/to tk/_}
                                   {::tk/on action-update, ::tk/to state-updating}
                                   {::tk/on action-stop, ::tk/to state-stopping}]}
                {::tk/name        state-partially-updated
                 ::tk/transitions [{::tk/on crud/action-edit, ::tk/to tk/_}
                                   {::tk/on action-update, ::tk/to state-updating}
                                   {::tk/on action-stop, ::tk/to state-stopping}]}
                {::tk/name        state-partially-started
                 ::tk/transitions [{::tk/on crud/action-edit, ::tk/to tk/_}
                                   {::tk/on action-update, ::tk/to state-updating}
                                   {::tk/on action-stop, ::tk/to state-stopping}]}
                {::tk/name        state-partially-stopped
                 ::tk/transitions [{::tk/on crud/action-edit, ::tk/to tk/_}
                                   {::tk/on action-force-delete, ::tk/to tk/_}
                                   {::tk/on action-start, ::tk/to state-starting}]}]
   ::tk/state  state-new})

(defn get-extra-operations
  [{:keys [id] :as resource}]
  (->> actions
       (map (fn [action]
              (when (sm/can-do-action? resource action)
                (u/action-map id action))))
       (remove nil?)))

(defn action-job-name
  [action]
  (str action "_deployment_set"))

(defn save-deployment-set
  [deployment-set]
  (-> deployment-set
      (u/update-timestamps)
      (crud/validate)
      (db/edit {:nuvla/authn auth/internal-identity})))

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
  [{:keys [environmental-variables] :as application}
   application-overwrite]
  (let [env (merge-env
              environmental-variables
              (:environmental-variables application-overwrite))]
    (cond-> application
            (seq env) (assoc :environmental-variables env))))

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
  ;; plan what to create
  ;; get existing deployments
  ;; diff to extract list of action to get to the plan

  ;; idea for difference algorithm
  #_(let [uno #{{:app-set     "set-1"
                 :application {:id      "module/fcc71f74-1898-4e38-a284-5997141801a7"
                               :version 0}
                 :credential  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
                {:app-set     "set-2"
                 :application {:id      "module/fcc71f74-1898-4e38-a284-5997141801a7"
                               :version 0}
                 :credential  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}}
          dos #{{:app-set     "set-2"
                 :application {:id      "module/fcc71f74-1898-4e38-a284-5997141801a7"
                               :version 0}
                 :credential  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}}
          ]
      (clojure.data/diff uno dos))

  (set
    (mapcat plan-set
            (module-utils/get-applications-sets applications-sets)
            (get-applications-sets deployment-set))))
