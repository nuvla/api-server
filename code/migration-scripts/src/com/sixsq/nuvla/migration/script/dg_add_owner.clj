(ns com.sixsq.nuvla.migration.script.dg-add-owner
  (:require [clojure.java.io :as io]
            [com.sixsq.nuvla.migration.api-client :as api]
            [com.sixsq.nuvla.auth.acl-resource :as a]))

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

(defn fetch-all-dg-without-owner
  []
  (let [dgs-data (->> (api/deployment-groups
                        {:filter  "owner=null"
                         :last    10000
                         :select  [:id :name :acl :owner]
                         :orderby "id:asc"})
                      :resources)]
    (reset! dgs dgs-data)
    (reset! dgs-by-id (->> dgs-data
                           (map (fn [{:keys [id] :as edge}]
                                  [id edge]))
                           (into {})))
    dgs-data))

(defn fetch-all-old-dg
  []
  (let [dgs-data (->> (api/deployment-groups
                        {:filter  "acl/edit-data='group/nuvla-admin'"
                         :last    10000
                         :select  [:id :name :acl :owner]
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

(defn fetch-all-deployments
  []
  (->> (api/deployment
         {:last    10000
          :select  [:id :name :acl :deployment-set]
          :orderby "id:asc"})
       :resources))

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

(defn add-owner-fix-acl-where-owner-missing
  "Add owner field to DGs that do not have one.
   Take it from the first one in the :owners field."
  []
  (let [dgs-data (fetch-all-old-dg)]
    (prn (count dgs-data))
    (doseq [{dg-id :id :keys [acl owner] :as _dg} dgs-data
            :let [owner (or owner (-> acl :owners first))]]
      (try
        (api/patch-resource dg-id
                            {:acl   {:edit-data [dg-id owner]
                                     :view-meta [dg-id owner]
                                     :view-data [dg-id owner]
                                     :edit-meta [dg-id owner]
                                     :view-acl  [dg-id owner]
                                     :manage    [dg-id owner]
                                     :delete    [owner]
                                     :owners    ["group/nuvla-admin"]}
                             :owner owner})
        (catch Exception ex
          (prn "Failed to update deployment group " dg-id ": " ex))))))

(defn add-deployment-set-to-dg-deployments-acl
  []
  (let [deployments-data (fetch-all-dg-deployments)
        filtered         (filter (fn [{:keys [deployment-set acl]}]
                                   (and (not (some #(= deployment-set %) (:edit-data acl)))
                                        (some? (api/get-resource-by-id deployment-set))))
                                 deployments-data)]
    (prn (count deployments-data) :filtered (count filtered))
    (doseq [{:keys [id deployment-set] :as deployment} filtered
            :when deployment-set
            :let [new-depl (reduce (fn [depl right-kw]
                                     (a/acl-append-resource depl right-kw deployment-set))
                                   deployment
                                   [:edit-data
                                    :delete
                                    :view-meta
                                    :view-data
                                    :manage
                                    :edit-meta])]]
      (try
        (api/patch-resource id (select-keys new-depl [:id :acl]))
        (catch Exception ex
          (prn "Failed to update deployment " id ": " ex))))))

(comment
  (api/reset-session)
  (api/ensure-admin-session
    ;:dev-alb
    :preprod
    ;:prod
    )

  (fetch-all-dg-without-owner)
  (count @dgs)
  (count (fetch-all-old-dg))
  (add-owner-fix-acl-where-owner-missing)

  (fetch-all-dg-deployments)
  (count @deployments)
  (->> @deployments
       (map :deployment-set)
       set
       count)
  (update-vals (->> @deployments (group-by :deployment-set)) count)
  ;(update-vals (->> @deployments (group-by :state)) count)
  ;(count @deployments)

  (add-deployment-set-to-dg-deployments-acl)
  )
