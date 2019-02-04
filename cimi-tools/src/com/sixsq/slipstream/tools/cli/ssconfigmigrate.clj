(ns com.sixsq.slipstream.tools.cli.ssconfigmigrate
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :refer [parse-opts]]
    [com.sixsq.slipstream.db.serializers.service-config-impl :as sci]
    [com.sixsq.slipstream.tools.cli.ssconfig :as ssconfig]
    [com.sixsq.slipstream.tools.cli.utils :as u]
    [taoensso.timbre :as log])
  (:gen-class))


(def default-db-binding-ns "com.sixsq.slipstream.db.es.loader")


;;
;; Dynamic vars.
;;
(def ^:dynamic *binding-ns* default-db-binding-ns)
(def ^:dynamic *c-names* #{})
(def ^:dynamic *cfg-path-url* nil)
(def ^:dynamic *creds* nil)
(def ^:dynamic *modifiers* #{})

;;
;; Helper functions.
;;

(defn warn-con-skipped
  [cin vals]
  (log/warn "WARNING: Skipped connector instance:" cin "\n"
            "WARNING: No connector name defined for:" cin "with attrs:" vals))

;;
;; Persistence.
;;

(defn con-name-known?
  [con]
  (not (str/blank? (:cloudServiceType con))))


(defn persist-config!
  [sc]
  (log/info "Persisting global configuration.")
  (-> sc
      sci/sc->cfg
      ssconfig/validate
      (u/modify-vals *modifiers*)
      ssconfig/store))


(defn persist-connector!
  [cin vals]
  (log/info "Persisting connector instance:" cin)
  (if (con-name-known? vals)
    (-> vals
        u/remove-attrs
        ssconfig/validate
        (u/modify-vals *modifiers*)
        ssconfig/store)
    (warn-con-skipped cin vals)))


(defn persist-connectors!
  [sc]
  (doseq [[cinkey vals] (sci/sc->connectors-vals-only sc *c-names*)]
    (if (seq (dissoc vals :id :cloudServiceType))
      (persist-connector! (name cinkey) vals)
      (log/warn "no data obtained for connector instance:" (name cinkey)))))


(defn run
  []
  (let [sc (u/cfg-path-url->sc *cfg-path-url*)]
    (ssconfig/init *binding-ns*)
    (persist-config! sc)
    (persist-connectors! sc)))

;;
;; Command line options processing.
;;


(def cli-options
  [["-b" "--binding BINDING_NS" "Database binding namespace."
    :id :binding-ns
    :default default-db-binding-ns]
   ["-c" "--connector CONNECTOR" "Connector instance names (category). If not provided all connectors will be stored."
    :id :connectors
    :default #{}
    :assoc-fn u/cli-parse-connectors]
   ["-x" "--configxml CONFIGXML" "Path to file or URL starting with https (requries -s parameter). Mandatory."]
   ["-s" "--credentials CREDENTIALS" "Credentials as user:pass for -x when URL is provided."]
   ["-m" "--modify old=new" "Modify in all values. '=' is a separator. Usefull for updating hostname/ip of SlipStream."
    :id :modifiers
    :default #{}
    :assoc-fn u/cli-parse-modifiers]
   ["-h" "--help"]
   [nil "--logging LEVEL" "Logging level: trace, debug, info, warn, error, fatal, or report."
    :id :level
    :default :info
    :parse-fn #(-> % str/lower-case keyword)]])


(def prog-help
  "
  Given SlipStream URL or path to file with configuration XML, extracts
  and stores the global service and per connector configuration
  parameters into DB backend identified by ES_HOST and ES_PORT env vars.")


(defn usage
  [options-summary]
  (->> [""
        "Migrates SlipStream service configuration from a running instance or XML file"
        "to DB backend identified by ES_HOST and ES_PORT env vars."
        ""
        "Usage: -x <path or URL> [-s <user:pass>] [-c <connector name>]"
        ""
        "Options:"
        options-summary
        prog-help]
       (str/join \newline)))


(defn -main
  [& args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]

    (log/set-level! (:level options :info))

    (cond
      (:help options) (u/exit 0 (usage summary))
      errors (u/exit 1 (u/error-msg errors)))
    (let [{:keys [configxml credentials connectors modifiers binding-ns]} options]
      (if configxml
        (alter-var-root #'*cfg-path-url* (constantly configxml))
        (u/failure (u/error-msg "-x parameter must be provided.")))
      (when binding-ns
        (alter-var-root #'*binding-ns* (constantly binding-ns)))
      (if (and (str/starts-with? configxml "https") (empty? credentials))
        (u/exit 1 (u/error-msg "-s must be provided when -x is URL."))
        (alter-var-root #'*creds* (constantly credentials)))
      (alter-var-root #'*c-names* (constantly connectors))
      (alter-var-root #'*modifiers* (constantly modifiers))))
  (run)
  (u/success))
