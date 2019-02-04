(ns com.sixsq.slipstream.ssclj.resources.quota.utils
  (:require
    [com.sixsq.slipstream.db.filter.parser :as parser]
    [com.sixsq.slipstream.db.impl :as db]
    [com.sixsq.slipstream.ssclj.middleware.cimi-params.utils :as cimi-params-utils]))


(defn extract-aggregation
  [aggregation response]
  (-> response
      first
      :aggregations
      (get (keyword aggregation))
      :value))


(defn get-cimi-params [{:keys [selection aggregation] :as quota}]
  (let [[func param] (cimi-params-utils/aggregation-clause aggregation)]
    {:first       1
     :last        0
     :filter      (parser/parse-cimi-filter selection)
     :aggregation {func [param]}}))


(defn quota-metric [{:keys [resource aggregation] :as quota} request]
  (let [options {:user-name   "INTERNAL"
                 :user-roles  ["ADMIN"]
                 :cimi-params (get-cimi-params quota)}]
    (->> options
         (db/query resource)
         (extract-aggregation aggregation))))


(defn quota-metric-user [{:keys [resource aggregation] :as quota} request]
  (let [options (merge
                  (select-keys request [:identity :user-name :user-roles])
                  {:cimi-params (get-cimi-params quota)})]
    (->> options
         (db/query resource)
         (extract-aggregation aggregation))))


(defn collect
  [quota request]
  (let [current-all (quota-metric quota request)
        current-user (quota-metric-user quota request)]
    (assoc quota :currentAll current-all
                 :currentUser current-user)))
