(ns com.sixsq.nuvla.server.resources.data.keys
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.server.util.response :as sr]
    [ring.util.response :as r]))


(defn valid-attribute-name?
  [valid-prefixes attr-name]
  (let [[ns _] (str/split (name attr-name) #":")]
    (valid-prefixes ns)))


(defn valid-attributes?
  [validator resource]
  (if-not (map? resource)
    true
    (and (every? validator (keys resource))
         (every? (partial valid-attributes? validator) (vals resource)))))


(defn throw-wrong-namespace
  []
  (let [code     406
        msg      "resource attributes do not satisfy defined namespaces"
        response (-> {:status code, :message msg}
                     sr/json-response
                     (r/status code))]
    (throw (ex-info msg response))))

