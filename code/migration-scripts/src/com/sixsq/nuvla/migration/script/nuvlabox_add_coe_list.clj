(ns com.sixsq.nuvla.migration.script.nuvlabox-add-coe-list
  (:require [clojure.string :as str]
            [com.sixsq.nuvla.migration.api-client :as api]))

(defonce nbs (atom nil))
(defonce nbs-by-id (atom nil))

(defn fetch-all-nuvlaboxes-without-coe-list
  []
  (let [nb-data (->> (api/edges
                       {:filter  "state='COMMISSIONED' and coe-list=null"
                        :last    10000
                        :select  [:id :infrastructure-service-group]
                        :orderby "id:asc"})
                     :resources)]
    (reset! nbs nb-data)
    (reset! nbs-by-id (->> nb-data
                           (map (fn [{:keys [id] :as edge}]
                                  [id edge]))
                           (into {})))
    nb-data))

(defonce infras (atom nil))
(defonce infras-by-id (atom nil))

(defn fetch-all-infras
  [infra-group-ids]
  (let [infra-data (->> (api/infrastructure-services
                          {:filter  (str "parent=['" (str/join "', '" infra-group-ids) "']")
                           :last    10000
                           :select  [:id :parent :name :subtype :swarm-enabled]
                           :orderby "id:asc"})
                        :resources)]
    (reset! infras infra-data)
    (reset! infras-by-id (->> infra-data
                              (map (fn [{:keys [id] :as infra}]
                                     [id infra]))
                              (into {})))
    infra-data))

(defn add-coe-list-to-nuvlaboxes
  []
  (let [nb-data         (fetch-all-nuvlaboxes-without-coe-list)
        infra-by-parent (->> (fetch-all-infras (keep :infrastructure-service-group nb-data))
                             (group-by :parent))]
    (doseq [{:keys [id infrastructure-service-group] :as _nuvlabox} nb-data
            :when infrastructure-service-group
            :let [infras (get infra-by-parent infrastructure-service-group)]
            :when (seq infras)
            :let [coe-list (keep (fn [{:keys [id subtype swarm-enabled]}]
                                   (when (#{"swarm" "kubernetes"} subtype)
                                     {:id       id
                                      :coe-type (case subtype
                                                  "swarm" (if swarm-enabled "swarm" "docker")
                                                  "kubernetes" "kubernetes")}))
                                 infras)]]
      (try
        (api/patch-resource id {:id       id
                                :coe-list (vec coe-list)})
        (prn "Updated nuvlabox " id " with :coe-list " coe-list)
        (catch Exception ex
          (prn "Failed to update nuvlabox " id ": " ex))))))

(comment
  (reset-session)
  (ensure-admin-session
    ;:local
    :dev-alb
    ;:preprod
    ;:prod
    )

  (fetch-all-nuvlaboxes-without-coe-list)
  (count @nbs)

  (fetch-all-infras (keep :infrastructure-service-group @nbs))
  (count @infras)
  (->> @infras)

  (add-coe-list-to-nuvlaboxes)
  )
