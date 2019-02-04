(ns com.sixsq.slipstream.ssclj.util.userparamsdesc
  (:require
    [com.sixsq.slipstream.ssclj.util.config :as uc])
  (:gen-class
    :name com.sixsq.slipstream.ssclj.util.UserParamsDesc
    :methods [#^{:static true} [getCloudDesc [java.lang.String] java.util.Map]
              #^{:static true} [getExecDesc [] java.util.Map]]))

(def ^:dynamic *user-cloud-params-desc* {})
(def ^:dynamic *user-exec-params-desc* {})

(defn- set-cloud-params!
  [cloud-name params]
  (alter-var-root #'*user-cloud-params-desc* assoc cloud-name params))

(defn- set-exec-params!
  [params]
  (alter-var-root #'*user-cloud-params-desc* (constantly params)))

(defn kw-to-str
  [m]
  (into {}
        (for [[k v] m]
          [(name k) (if (map? v) (kw-to-str v) v)])))

(defn slurp-cloud-cred-desc
  [cloud-name]
  (uc/read-config
    (format "com/sixsq/slipstream/connector/%s-cloud-cred-desc.edn" cloud-name)))

(defn slurp-exec-params-desc
  []
  (uc/read-config "com/sixsq/slipstream/ssclj/resources/user-exec-params-desc.edn"))

(defn -getCloudDesc
  [cloud-name]
  (if (and (bound? #'*user-cloud-params-desc*)
           (contains? *user-cloud-params-desc* cloud-name))
    (get *user-cloud-params-desc* cloud-name)
    (let [params-map (-> cloud-name
                         slurp-cloud-cred-desc
                         kw-to-str)]
      (set-cloud-params! cloud-name params-map)
      params-map)))

(defn -getExecDesc
  []
  (if (zero? (count *user-exec-params-desc*))
    (let [params-map (kw-to-str (slurp-exec-params-desc))]
      (set-exec-params! params-map)
      params-map)
    *user-exec-params-desc*))
