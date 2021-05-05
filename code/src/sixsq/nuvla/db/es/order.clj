(ns sixsq.nuvla.db.es.order)


(def sort-order {:asc  "asc"
                 :desc "desc"})


(defn direction->sort-order
  "Returns the elasticsearch SortOrder constant associated with the :asc and
   :desc keywords. Any other value for direction will result in an
   IllegalArgumentException being thrown."
  [direction]
  (or (sort-order direction)
      (throw (IllegalArgumentException. (str "invalid sorting direction '" direction "', must be :asc or :desc")))))


(defn sort-entry
  "Give a tuple with the field-name and direction, adds the sort clause to the
   request builder."
  [[field-name direction]]
  {field-name (direction->sort-order direction)})


(defn sorters
  "Given the sorting information in the :cimi-params parameter, add all of the
   sorting clauses to the sort map."
  [{:keys [orderby] :as _cimi-params}]
  (let [entries (mapv sort-entry orderby)]
    (when (seq entries)
      {:sort entries})))
