(ns com.sixsq.slipstream.db.es-rest.select)

(defn select
  "Adds the list of keys to select from the returned documents."
  [{:keys [select] :as cimi-params}]
  (when select
    {:_source (-> select vec (conj "acl"))}))
