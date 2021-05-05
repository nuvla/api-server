(ns sixsq.nuvla.db.es.select)

(defn select
  "Adds the list of keys to select from the returned documents."
  [{:keys [select] :as _cimi-params}]
  (when select
    {:_source (-> select vec (conj "acl"))}))
