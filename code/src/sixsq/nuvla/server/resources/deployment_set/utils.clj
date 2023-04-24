(ns sixsq.nuvla.server.resources.deployment-set.utils
  (:require [clojure.string :as str]
            [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.resources.module.utils :as module-utils]
            [sixsq.nuvla.server.util.response :as r]))

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
  (get app-set :targets []))

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
    {:credential  t
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

(defn resolve-application
  [href request]
  (-> (crud/retrieve {:params         (u/id->request-params href)
                      :request-method :get
                      :nuvla/authn    (auth/current-authentication request)})
      r/throw-response-not-200
      :body))
