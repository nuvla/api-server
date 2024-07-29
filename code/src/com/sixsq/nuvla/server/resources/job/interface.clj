(ns com.sixsq.nuvla.server.resources.job.interface
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.server.util.response :as r]))


(defn get-context->response
  [& resources]
  (->> resources
       (remove nil?)
       (mapcat #(if (map? %) (vector %) %))
       (map #(vector (:id %) (dissoc % :operations :acl)))
       (into {})
       r/json-response))


(defn dispatch-on-target-type-and-action
  [{:keys [target-resource action] :as _resource}]
  [(some-> target-resource :href (str/split #"/") first) action])

(defmulti get-context dispatch-on-target-type-and-action)

(defmethod get-context :default
  [_resource]
  (get-context->response))


(defmulti on-cancel dispatch-on-target-type-and-action)

(defmethod on-cancel :default
  [_job]
  nil)


(defmulti on-timeout dispatch-on-target-type-and-action)

(defmethod on-timeout :default
  [_job]
  nil)


(defmulti on-done dispatch-on-target-type-and-action)

(defmethod on-done :default
  [_job]
  nil)

