(ns com.sixsq.slipstream.db.es.pagination
  (:require
    [com.sixsq.slipstream.db.es.common.pagination :as paging])
  (:import
    (org.elasticsearch.action.search SearchRequestBuilder)))

(defn add-paging
  "Adds the paging parameters 'from' and 'size' to the request builder based
   on the 'first' and 'last' CIMI parameter values. Note that a 'last' value of
   zero is a special case that always results in a size of zero."
  [^SearchRequestBuilder request-builder {:keys [first last] :as cimi-params}]
  (let [[from size] (paging/es-paging-params first last)]
    (.. request-builder
        (setFrom from)
        (setSize size))))
