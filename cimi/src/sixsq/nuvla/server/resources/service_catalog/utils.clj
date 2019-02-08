(ns sixsq.nuvla.server.resources.service-catalog.utils
  (:require
    [clojure.string :as str]
    [ring.util.response :as r]
    [sixsq.nuvla.util.response :as sr]))


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
  (let [code 406
        msg "resource attributes do not satisfy defined namespaces"
        response (-> {:status code, :message msg}
                     sr/json-response
                     (r/status code))]
    (throw (ex-info msg response))))

