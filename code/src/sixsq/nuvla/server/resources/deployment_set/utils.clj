(ns sixsq.nuvla.server.resources.deployment-set.utils
  (:require [clojure.string :as str]
            [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.db.impl :as db]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.state-machine :as sm]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.resources.module.utils :as module-utils]
            [statecharts.core :as fsm]))

(def action-start :start)
(def action-stop :stop)
(def action-update :update)
(def action-cancel :cancel)
(def action-edit :edit)
(def action-delete :delete)
(def action-ok :ok)
(def action-nok :nok)
(def action-force-delete :force-delete)
(def action-plan :plan)

(def actions [action-start action-stop action-update action-cancel
              action-edit action-delete action-ok action-nok action-force-delete
              action-plan])


(def state-new :new)
(def state-starting :starting)
(def state-started :started)
(def state-stopping :stopping)
(def state-stopped :stopped)
(def state-partially-started :partially-started)
(def state-partially-updated :partially-updated)
(def state-partially-stopped :partially-stopped)
(def state-updating :updating)
(def state-updated :updated)

(def states-map {state-new               "NEW"
                 state-starting          "STARTING"
                 state-started           "STARTED"
                 state-stopping          "STOPPING"
                 state-stopped           "STOPPED"
                 state-partially-started "PARTIALLY-STARTED"
                 state-partially-updated "PARTIALLY-UPDATED"
                 state-partially-stopped "PARTIALLY-STOPPED"
                 state-updating          "UPDATING"
                 state-updated           "UPDATED"})
(def states (vec (vals states-map)))

(def state-machine
  (fsm/machine
    {:id      :deployment-group
     :initial state-new
     :states
     {state-new               {:on {action-start  state-starting
                                    action-edit   {}
                                    action-delete {}}}
      state-starting          {:on {action-cancel state-partially-started
                                    action-ok     state-started
                                    action-nok    state-partially-started}}
      state-started           {:on {action-edit   {}
                                    action-update state-updating
                                    action-stop   state-stopping}}
      state-stopping          {:on {action-cancel state-partially-stopped
                                    action-ok     state-stopped
                                    action-nok    state-partially-stopped}}
      state-updating          {:on {action-cancel state-partially-updated
                                    action-ok     state-updated
                                    action-nok    state-partially-updated}}
      state-stopped           {:on {action-edit   {}
                                    action-delete {}
                                    action-start  state-starting}}
      state-updated           {:on {action-edit   {}
                                    action-stop   state-stopping
                                    action-update state-updating}}
      state-partially-updated {:on {action-edit   {}
                                    action-stop   state-stopping
                                    action-update state-updating}}
      state-partially-started {:on {action-edit   {}
                                    action-stop   state-stopping
                                    action-update state-updating}}
      state-partially-stopped {:on {action-edit         {}
                                    action-force-delete {}
                                    action-start        state-starting}}}}))

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
