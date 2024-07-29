(ns com.sixsq.nuvla.db.es.select)

(defn select
  "Adds the list of keys to select from the returned documents."
  [{:keys [select] :as _cimi-params}]
  (when select
    {:_source (-> select (conj "id" "acl" "resource-type" "state") vec)}))
