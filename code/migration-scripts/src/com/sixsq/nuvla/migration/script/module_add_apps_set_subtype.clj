(ns com.sixsq.nuvla.migration.script.module-add-apps-set-subtype
  (:require [clojure.string :as str]
            [com.sixsq.nuvla.migration.api-client :as api]
            [com.sixsq.nuvla.server.resources.spec.deployment-set :as spec]
            [com.sixsq.nuvla.server.resources.spec.module :as module-spec]))

(defn fetch-all-modules-without-apps-set-subtype
  []
  (let [modules-data (->> (api/module
                            {:filter  "subtype='applications_sets' and apps-set-subtype=null"
                             :last    10000
                             :select  [:id :path]
                             :orderby "id:asc"})
                          :resources)]
    (filter #(not (str/starts-with? (:path %) "apps-sets/")) modules-data)))

(defn fetch-full-modules
  [module-ids]
  (map api/get-resource-by-id module-ids))

(defn add-apps-set-subtype-where-missing
  "Add apps-set-subtype field to apps-set modules that do not have the field set.
   Take it from the content/applications-sets[0]/subtype."
  []
  (let [modules-data (->> (fetch-all-modules-without-apps-set-subtype)
                          (map :id)
                          (fetch-full-modules))]
    (prn (count modules-data))
    (doseq [{module-id :id :keys [content] :as _module} modules-data]
      (let [first-apps-set-subtype (-> content :applications-sets first :subtype)]
        (try
          (api/patch-resource module-id
                              {:apps-set-subtype first-apps-set-subtype})
          (catch Exception ex
            (prn "Failed to update module " module-id ": " ex)))))))

(comment
  (api/reset-session)
  (api/ensure-admin-session
    ;:local
    :dev-alb
    ;:preprod
    ;:prod
    )

  (fetch-all-modules-without-apps-set-subtype)
  (add-apps-set-subtype-where-missing))
