(ns com.sixsq.nuvla.migration.script.dg-add-subtype
  (:require [clojure.string :as str]
            [com.sixsq.nuvla.migration.api-client :as api]
            [com.sixsq.nuvla.server.resources.spec.deployment-set :as spec]
            [com.sixsq.nuvla.server.resources.spec.module :as module-spec]))

(defn edge-availability-raw-data
  [edge-id]
  (get-in (api/data {:dataset     ["availability-stats"]
                     :filter      (str "id='" edge-id "'")
                     :from        "2010-01-01T00:00:00Z"
                     :to          "2024-12-31T23:59:59Z"
                     :granularity "raw"})
          [:availability-stats 0 :ts-data]))

(defonce dgs (atom nil))
(defonce dgs-by-id (atom nil))

(defn fetch-all-dg-without-subtype
  []
  (let [dgs-data (->> (api/deployment-groups
                        {:filter  "subtype=null"
                         :last    10000
                         :select  [:id :name :applications-sets]
                         :orderby "id:asc"})
                      :resources)]
    (reset! dgs dgs-data)
    (reset! dgs-by-id (->> dgs-data
                           (map (fn [{:keys [id] :as dg}]
                                  [id dg]))
                           (into {})))
    dgs-data))

(defonce deployments (atom nil))
(defonce deployments-by-id (atom nil))

(defn fetch-all-dg-deployments
  []
  (let [deployments-data (->> (api/deployment
                                {:filter  "deployment-set!=null"
                                 :last    10000
                                 :select  [:id :name :acl :deployment-set :state]
                                 :orderby "id:asc"})
                              :resources)]
    (reset! deployments deployments-data)
    (reset! deployments-by-id (->> deployments-data
                                   (map (fn [{:keys [id] :as deployment}]
                                          [id deployment]))
                                   (into {})))
    deployments-data))

(defn add-subtype-where-subtype-missing
  "Add subtype field to DGs that do not have one.
   Take it from the modules :subtype and :compatibility fields."
  []
  (let [dgs-data (fetch-all-dg-without-subtype)]
    (prn (count dgs-data))
    (doseq [{dg-id :id :keys [applications-sets] :as _dg} dgs-data]
      (let [{app-set-id :id app-set-version :version} (first applications-sets)
            {:keys [applications-sets]} (api/module-version app-set-id app-set-version)
            apps            (get-in applications-sets [0 :applications])
            apps-data       (->> (api/module
                                   {:filter  (str "id=['" (str/join "', '" (map :id apps)) "']")
                                    :last    10000
                                    :select  [:id :name :subtype :compatibility]
                                    :orderby "id:asc"})
                                 :resources)
            subtypes        (distinct (map :subtype apps-data))
            compatibilities (distinct (map :compatibility apps-data))]
        (try
          (if (> (count subtypes) 1)
            (prn dg-id "more than one app subtype found for dg"
                 {:subtypes subtypes :compatibilities compatibilities}))
          (let [module-subtype (first subtypes)
                new-subtype    (cond (zero? (count apps-data))
                                     (do
                                       (prn dg-id "has no apps. Setting default subtype docker-compose")
                                       ;; no apps => docker-compose subtype by default
                                       spec/subtype-docker-compose)

                                     (and (= module-spec/subtype-app-docker module-subtype)
                                          (every? #(= module-spec/compatibility-docker-compose %) compatibilities))
                                     spec/subtype-docker-compose

                                     (and (= module-spec/subtype-app-docker module-subtype)
                                          (some #(= module-spec/compatibility-swarm %) compatibilities))
                                     spec/subtype-docker-swarm

                                     (#{module-spec/subtype-app-k8s module-spec/subtype-app-helm} module-subtype)
                                     spec/subtype-kubernetes)]
            (api/patch-resource dg-id
                                {:subtype new-subtype}))
          (catch Exception ex
            (prn "Failed to update deployment group " dg-id ": " ex)))))))

(comment
  (api/reset-session)
  (api/ensure-admin-session
    ;:dev-alb
    :preprod
    ;:prod
    )

  (fetch-all-dg-without-subtype)
  (count @dgs)
  (add-subtype-where-subtype-missing)
  )
