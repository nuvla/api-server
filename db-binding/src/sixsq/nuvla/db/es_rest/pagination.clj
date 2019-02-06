(ns sixsq.nuvla.db.es-rest.pagination
  (:require
    [sixsq.nuvla.db.es.common.pagination :as paging]))

(defn paging
  "Creates a map with the from and size parameters to limit the responses from
   an Elasticsearch query."
  [{:keys [first last] :as cimi-params}]
  (let [[from size] (paging/es-paging-params first last)]
    {:from from, :size size}))
