(ns com.sixsq.slipstream.tools.cli.utils
  (:require
    [clj-http.client :as http]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [com.sixsq.slipstream.db.serializers.service-config-util :as scu]
    [sixsq.slipstream.client.api.authn :as authn]
    [sixsq.slipstream.client.api.cimi :as cimi]
    [sixsq.slipstream.client.sync :as cimi-sync]
    [taoensso.timbre :as log]))


(def ^:dynamic *cimi-client* nil)


(defn cep-url
  [endpoint]
  (str endpoint "/api/cloud-entry-point"))


(defn create-cimi-client
  [cep-url]
  (alter-var-root #'*cimi-client* (constantly (cimi-sync/instance cep-url))))


(defn login
  [username password]
  (when *cimi-client*
    (let [creds {:href     "session-template/internal"
                 :username username
                 :password password}
          {:keys [status message] :as response} (authn/login *cimi-client* creds)]
      (when (not= 201 status)
        (let [msg (cond-> (str "login failed with status (" status ")")
                          message (str ": " message))]
          (log/error msg)
          (throw (ex-info msg response)))))))


(defn ss-cfg
  []
  (when *cimi-client*
    (cimi/get *cimi-client* "configuration/slipstream")))


(defn ss-connectors
  []
  (when *cimi-client*
    (:connectors (cimi/search *cimi-client* "connectors" {:$last 500}))))


(defn split-creds
  "Splits a credentials string formatted as 'username:password' into a tuple
   [username password]."
  [creds]
  (if (string? creds)
    (let [[username password] (str/split creds #":")]
      [username password])
    [nil nil]))


(defn replace-value
  [value [pattern replacement]]
  (if (string? value)
    (str/replace value pattern replacement)
    value))


(defn update-val
  [modifiers [_ v]]
  (reduce replace-value v modifiers))


(defn modify-vals
  "Uses the list of modifiers to replace string values in the values at the
   first level of the resource map."
  [resource modifiers]
  (let [convert-fn (partial update-val modifiers)]
    (->> resource
         (map (juxt first convert-fn))
         (into {}))))


(def con-attrs-to-remove
  {"ec2"            [:securityGroup]
   "nuvlabox"       [:orchestratorInstanceType :pdiskEndpoint]
   "stratuslab"     [:messagingQueue :messagingType :messagingEndpoint]
   "stratuslabiter" [:messagingQueue :messagingType :messagingEndpoint]})


(defn remove-attrs
  "Cleanup of old attributes."
  [con]
  (if-let [attrs (get con-attrs-to-remove (:cloudServiceType con))]
    (apply dissoc con attrs)
    con))


(defn ss-cfg-url
  [url]
  (str url "/api/configuration/slipstream"))


(defn cfg-json
  [endpoint-url]
  (-> endpoint-url
      ss-cfg-url
      (http/get {:follow-redirects false
                 :accept           :json})
      :body))


(defn cfg-path-url->sc
  [endpoint-url]
  (cfg-json endpoint-url))

;;
;; file read/write utilities
;;

(defn slurp-edn
  [f]
  (edn/read-string (slurp f)))


(defn spit-edn
  [obj f]
  (scu/spit-pprint obj f))


;;
;; resource type utilities
;;

(defn resource-type-from-str
  "Returns the base resource type from the string or nil if the type cannot be
   determined."
  [type-string]
  (let [type (some-> type-string
                     (str/split #"(-template)?/")
                     first)]
    (when-not (str/blank? type)
      type)))


(defn resource-type
  "Extracts the resource type from a string, resource, or request. Returns nil
   if the resource type cannot be obtained."
  [v]
  (resource-type-from-str
    (cond
      (string? v) v
      (:id v) (:id v)
      :else (-> v :body :id))))

;;
;; utilities for dealing with command line args and shell
;;

(defn exit
  ([]
   (exit 0 nil))
  ([status]
   (exit status nil))
  ([status msg]
   (when msg
     (if-not (zero? status)
       (log/error msg)
       (log/info msg)))
   (System/exit status)))


(defn success
  ([]
   (exit))
  ([msg]
   (exit 0 msg)))


(defn failure
  [& msg]
  (exit 1 (str/join msg)))


(defn error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))


(defn cli-parse-sets
  ([m k v]
   (cli-parse-sets m k v identity))
  ([m k v f] (assoc m k (if-let [oldval (get m k)]
                          (merge oldval (f v))
                          (hash-set (f v))))))


(defn cli-parse-connectors
  [m k v]
  (cli-parse-sets m k v))


(defn parse-replacement
  "Parses replacement specifications of the form 'm=r' as the two-element
   tuple [#'m' 'r']."
  [replacement]
  (let [[m r] (str/split replacement #"=")]
    [(re-pattern m) r]))


(defn cli-parse-modifiers
  [m k v]
  (cli-parse-sets m k v parse-replacement))

