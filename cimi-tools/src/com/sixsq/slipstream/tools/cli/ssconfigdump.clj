(ns com.sixsq.slipstream.tools.cli.ssconfigdump
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [com.sixsq.slipstream.db.utils.common :as db-utils]
    [com.sixsq.slipstream.tools.cli.utils :as u]
    [taoensso.timbre :as log])
  (:gen-class))


(def ^:dynamic *options* nil)


(defn remove-nuvlabox-connectors
  [{:keys [cloudConnectorClass] :as configuration}]
  (let [connector-classes (->> (str/split cloudConnectorClass #"\s*,\s*")
                               (remove #(re-matches #".*nuvlabox$" %))
                               (str/join ",\r\n"))]
    (assoc configuration :cloudConnectorClass connector-classes)))


(defn dump-configuration!
  [ss-cfg]
  (log/info "saving SlipStream configuration")
  (-> ss-cfg
      remove-nuvlabox-connectors
      (u/modify-vals (:modifiers *options*))
      (assoc :reportsObjectStoreBucketName "<REPORTS_BUCKET>")
      (assoc :reportsObjectStoreCreds "<CREDENTIAL_ID>")
      (u/spit-edn "configuration-slipstream.edn")))


(defn dump-connector!
  [connector-name connector]
  (log/info "saving connector:" connector-name)
  (let [filename (format "connector-%s.edn" connector-name)]
    (-> connector
        u/remove-attrs
        (u/spit-edn filename))))


(defn dump-connectors!
  [connectors]
  (doseq [{:keys [id cloudServiceType] :as connector} connectors]
    (let [connector-name (second (db-utils/split-id id))
          updated-connector (-> connector
                                (u/modify-vals (:modifiers *options*)))]
      (when-not (= "nuvlabox" cloudServiceType)
        (dump-connector! connector-name updated-connector)))))


(defn run
  []
  (-> *options* :endpoint u/cep-url u/create-cimi-client)
  (let [[username password] (-> *options* :credentials u/split-creds)]
    (u/login username password))
  (let [ss-cfg (u/ss-cfg)]
    (dump-configuration! ss-cfg))
  (let [ss-connectors (u/ss-connectors)]
    (dump-connectors! ss-connectors)))

;;
;; Command line options processing.
;;

(def cli-options
  [["-c" "--connector CONNECTOR" "Connector instance names (category). If not provided all connectors will be stored."
    :id :connectors
    :default #{}
    :assoc-fn u/cli-parse-connectors]
   ["-e" "--endpoint ENDPOINT_URL" "Server endpoint URL. Mandatory."]
   ["-s" "--credentials CREDENTIALS" "Credentials as username:password. Mandatory."]
   ["-m" "--modify old=new" "Modify in all values. '=' is a separator. Useful for updating hostname/ip of SlipStream."
    :id :modifiers
    :default #{}
    :assoc-fn u/cli-parse-modifiers]
   [nil "--skip-no-connector-name" "Don't dump invalid configurations."]
   ["-h" "--help"]
   [nil "--logging LEVEL" "Logging level: trace, debug, info, warn, error, fatal, or report."
    :id :level
    :default :info
    :parse-fn #(-> % str/lower-case keyword)]])


(def prog-help
  "
  Given SlipStream URL or path to file with configuration XML, extracts and
  stores global service configuration and per connector parameters, with their
  description, into configuration-slipstream.edn, connector-<instance-name>.edn
  and connector-<instance-name>-desc.edn respectively.")


(defn usage
  [options-summary]
  (str/join \newline
            [""
             "Extracts and stores service configuration, connector parameters
             and their description."
             ""
             "Usage: -e <URL> -s <user:pass> [-c <connector name>]"
             ""
             "Options:"
             options-summary
             prog-help]))


(defn options-errors
  [{:keys [endpoint credentials] :as options}]
  (cond-> []
          (nil? endpoint) (conj "-e (--endpoint) parameter must be provided")
          (nil? credentials) (conj "-s (--credentials) parameter must be provided")
          true seq))


(defn -main
  [& args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]

    (log/set-level! (:level options :info))
    (log/debug "parsed options:\n" (with-out-str (pprint options)))

    (cond
      (:help options) (u/success (usage summary))
      errors (u/failure (u/error-msg errors)))

    (when-let [errors (options-errors options)]
      (u/failure (u/error-msg errors)))

    (alter-var-root #'*options* (constantly options)))

  (run)
  (u/success))

