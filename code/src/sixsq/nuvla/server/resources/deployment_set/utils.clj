(ns sixsq.nuvla.server.resources.deployment-set.utils
  (:require [clojure.string :as str]
            [sixsq.nuvla.server.resources.module.utils :as module-utils]
            [sixsq.nuvla.server.util.response :as r]))

(def state-new "NEW")
(def state-creating "CREATING")
(def state-created "CREATED")
(def state-starting "STARTING")
(def state-started "STARTED")
(def state-stopping "STOPPING")
(def state-stopped "STOPPED")

(def action-create "create")
(def action-start "start")
(def action-stop "stop")
(def action-plan "plan")

(defn state-new?
  [{:keys [state] :as _resource}]
  (= state state-new))

(defn can-create?
  [resource]
  (state-new? resource))

(defn can-start?
  [{:keys [state] :as _resource}]
  (contains? #{state-new state-created state-stopped} state))

(defn can-stop?
  [{:keys [state] :as _resource}]
  (contains? #{state-started} state))

(defn action-job-name
  [action]
  (str action "_deployment_set"))

(defn throw-can-not-do-action
  [{:keys [id state] :as resource} pred action]
  (if (pred resource)
    resource
    (throw (r/ex-response (format "invalid state (%s) for %s on %s"
                                  state action id) 409 id))))

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
