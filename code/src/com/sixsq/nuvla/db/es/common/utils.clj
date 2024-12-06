(ns com.sixsq.nuvla.db.es.common.utils
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.db.utils.common :as cu]))

(def default-index-prefix "nuvla-")
(def index-prefix-wildcard (str default-index-prefix "*"))

(defn id->index
  ([id]
   (id->index default-index-prefix id))
  ([index-prefix id]
   (->> id cu/split-id first (str index-prefix))))

(defn collection-id->index
  ([collection-id]
   (collection-id->index default-index-prefix collection-id))
  ([index-prefix collection-id]
   (str index-prefix (name collection-id))))

(defn summarise-bulk-operation-response
  [{:keys [took errors items] :as _response}]
  (->> items
       (map #(get-in % [:update :result]))
       frequencies
       (map (fn [[k v]] (str k ": " v)))
       (concat ["errors:" errors
                "took:" (str took "ms")])
       (str/join " ")))
