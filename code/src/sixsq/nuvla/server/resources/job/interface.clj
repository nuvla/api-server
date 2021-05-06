(ns sixsq.nuvla.server.resources.job.interface
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.server.util.response :as r]))


(defn get-context->response
  [& resources]
  (->> resources
       (remove nil?)
       (mapcat #(if (map? %) (vector %) %))
       (map #(vector (:id %) (dissoc % :operations :acl)))
       (into {})
       r/json-response))


(defmulti get-context (fn [{:keys [target-resource action] :as _resource}]
                        [(some-> target-resource :href (str/split #"/") first) action]))

(defmethod get-context :default
  [_resource]
  (get-context->response))