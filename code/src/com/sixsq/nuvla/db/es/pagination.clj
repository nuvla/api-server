(ns com.sixsq.nuvla.db.es.pagination
  (:require
    [com.sixsq.nuvla.db.es.common.pagination :as paging]))

(defn paging
  "Creates a map with the from and size parameters to limit the responses from
   an Elasticsearch query."
  [{:keys [first last] :as _cimi-params}]
  (let [[from size] (paging/es-paging-params first last)]
    {:from from, :size size}))


;
#_(defn add-paging
    "Adds the paging parameters 'from' and 'size' to the request builder based
     on the 'first' and 'last' CIMI parameter values. Note that a 'last' value of
     zero is a special case that always results in a size of zero."
    [^SearchRequestBuilder request-builder {:keys [first last] :as cimi-params}]
    (let [[from size] (paging/es-paging-params first last)]
      (.. request-builder
          (setFrom from)
          (setSize size))))
