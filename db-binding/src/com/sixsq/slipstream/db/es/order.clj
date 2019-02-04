(ns com.sixsq.slipstream.db.es.order
  (:import
    (org.elasticsearch.action.search SearchRequestBuilder)
    (org.elasticsearch.search.sort SortOrder)))

(def sort-order {:asc  SortOrder/ASC
                 :desc SortOrder/DESC})

(defn direction->sort-order
  "Returns the elasticsearch SortOrder constant associated with the :asc and
   :desc keywords. Any other value for direction will result in an
   IllegalArgumentException being thrown."
  [direction]
  (or (sort-order direction)
      (throw (IllegalArgumentException. (str "invalid sorting direction '" direction "', must be :asc or :desc")))))

(defn add-sorter
  "Give a tuple with the field-name and direction, adds the sort clause to the
   request builder. Intended to be used in a reduction."
  [^SearchRequestBuilder request-builder [field-name direction]]
  (.addSort request-builder field-name (direction->sort-order direction)))

(defn add-sorters
  "Given the sorting information in the :cimi-params parameter, add all of the
   sorting clauses to the request builder."
  [request-builder {:keys [orderby] :as cimi-params}]
  (reduce add-sorter request-builder orderby))
