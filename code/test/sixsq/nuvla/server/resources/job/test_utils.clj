(ns sixsq.nuvla.server.resources.job.test-utils
  (:require [clojure.string :as str]
            [sixsq.nuvla.db.filter.parser :as parser]
            [sixsq.nuvla.server.resources.common.crud :as crud]
            [sixsq.nuvla.server.resources.job :as job]))

(defn query-jobs
  [{:keys [target-resource action parent-job orderby last] :as _opts}]
  (some-> job/resource-type
          (crud/query-as-admin
            {:cimi-params
             (cond->
               {:filter (parser/parse-cimi-filter
                          (str/join " and "
                                    (cond-> []
                                            target-resource (conj (str "target-resource/href='" target-resource "'"))
                                            action (conj (str "action='" action "'"))
                                            parent-job (conj (str "parent-job='" parent-job "'")))))}
               orderby (assoc :orderby orderby)
               last (assoc :last last))})
          second))
