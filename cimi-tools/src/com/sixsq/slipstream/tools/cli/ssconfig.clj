(ns com.sixsq.slipstream.tools.cli.ssconfig
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [com.sixsq.slipstream.db.loader :as db-loader]
    [com.sixsq.slipstream.db.serializers.service-config-impl :as sci]
    [com.sixsq.slipstream.db.serializers.utils :as su]
    [com.sixsq.slipstream.ssclj.resources.configuration :as cfg]
    [com.sixsq.slipstream.ssclj.resources.configuration-slipstream :as cfg-s]
    [com.sixsq.slipstream.ssclj.resources.configuration-template :as cfgt]
    [com.sixsq.slipstream.ssclj.resources.connector :as conn]
    [com.sixsq.slipstream.ssclj.resources.connector-template :as cont]
    [com.sixsq.slipstream.tools.cli.utils :as u :refer :all]
    [taoensso.timbre :as log])
  (:gen-class))


(def default-db-binding-ns "com.sixsq.slipstream.db.es.loader")


(def connector-resource conn/resource-url)
(def configuration-resource cfg/resource-url)
(def resource-types #{connector-resource configuration-resource})

(def mandatory-attrs #{:id})

(def required-attrs {configuration-resource #{}
                     connector-resource     #{:cloudServiceType}})

(def ^:dynamic *templates*)
(def ^:dynamic *resources*)
(def ^:dynamic *delete-resources*)

;;
;; Helper functions.
;;

(defn resp-success?
  [resp]
  (< (:status resp) 400))


(def resp-error? (complement resp-success?))


(defn exit-on-resp-error
  [resp]
  (if (resp-error? resp)
    (let [msg (or (-> resp :body :message) (:message resp))]
      (u/failure "Failed with: " msg ".")))
  resp)


(defn init-db-client
  [binding-ns]
  (db-loader/load-and-set-persistent-db-binding binding-ns))


(defn init-namespaces
  []
  (su/initialize)
  (alter-var-root #'*templates* (constantly {:connector     @cont/templates
                                             :configuration @cfgt/templates})))


(defn init
  [binding-ns]
  (init-db-client binding-ns)
  (init-namespaces))


;;
;; Common functions.
;;

(defn connector?
  "c - str or map."
  [c]
  (= (u/resource-type c) conn/resource-url))


(defn configuration?
  "c - str or map."
  [c]
  (= (u/resource-type c) cfg/resource-url))


(defn check-mandatory-attrs
  [m]
  (if-not (set/subset? mandatory-attrs (-> m
                                           keys
                                           set))
    (u/failure "Each resource must contain the mandatory attributes: " (str/join ", " mandatory-attrs))
    m))


(defn validate-resource-type
  [m]
  (if-not (-> m u/resource-type resource-types)
    (u/failure "Resource type must be one of: " (str/join ", " resource-types))
    m))


(defn resource->template-name
  [{:keys [id] :as c}]
  (cond
    (configuration? c) (str/replace-first id #"/" "-template/")
    (connector? c) (-> id
                       (str/split #"/")
                       first
                       (str "-template/")
                       (str (:cloudServiceType c)))))


(defn get-templates
  [c]
  (cond
    (configuration? c) (:configuration *templates*)
    (connector? c) (:connector *templates*)
    :else {}))


(defn halt-no-template
  [c]
  (let [tname (resource->template-name c)
        templates (get-templates c)]
    (when-not (contains? templates tname)
      (u/failure "No template available for " tname))
    c))


(defn check-required-attrs
  [c]
  (let [rtype (u/resource-type c)
        attrs (get required-attrs rtype)]
    (if-not (every? #(contains? c %) attrs)
      (u/failure "Resource '" rtype "' must contain the following attributes: " (str/join ", " attrs))
      c)))


(defn validate
  [c]
  (-> c
      check-mandatory-attrs
      validate-resource-type
      check-required-attrs
      halt-no-template))


;;
;; Service configuration.
;;

(defn cfg-add-service-attr
  [{:keys [service] :as m}]
  (cond-> m
          (nil? service) (assoc :service cfg-s/service)))


(defn cfg-tpl-slipstream
  []
  (get (:configuration *templates*) (str cfgt/resource-url "/" cfg-s/service)))


(defn complete-config
  "Add missing keys to satisfy schema."
  [cfg]
  (->> cfg
       cfg-add-service-attr
       (merge (cfg-tpl-slipstream))))


(defn cfg-edit-or-add
  "Returns response of edit or add operation. Tries to add if edit fails with 404."
  [config-req]
  (let [edit-resp (cfg/edit-impl config-req)]
    ;; adding may fail because template hasn't been registered yet.
    (if (= 404 (:status edit-resp))
      (do
        (log/info "Adding configuration for the first time.")
        (-> config-req
            (assoc :body (complete-config (:body config-req)))
            cfg/add-impl))
      edit-resp)))


(defn store-cfg
  [config]
  (-> config
      sci/cfg-as-request
      cfg-edit-or-add
      exit-on-resp-error))

;;
;; Connectors.
;;

(defn conn-inst-name
  [conn]
  (second (str/split (:id conn) #"/")))


(defn complete-connector
  "Add missing keys to satisfy schema."
  [conn]
  (let [ctn (str cont/resource-url "/" (:cloudServiceType conn))]
    (-> (get (:connector *templates*) ctn {})
        (merge conn)
        (assoc :instanceName (conn-inst-name conn)))))


(defn get-ccc
  []
  (:cloudConnectorClass (sci/load-cfg)))


(defn merge-ccc-and-new-conn
  [ccc new-conn]
  (str/replace (str/join "," [ccc new-conn]) #"(^[ \t]*,*|,*[ \t]*$)" ""))


(defn update-ccc
  [ccc cin cn]
  (let [new-conn (str/join ":" [cin cn])]
    (log/info "Updating :cloudConnectorClass parameter with -" new-conn)
    (store-cfg {:id                  (str cfg/resource-url "/" cfg-s/service)
                :cloudConnectorClass (merge-ccc-and-new-conn ccc new-conn)})))


(defn add-conn-to-ccc
  "Adds connector to :cloudConnectorClass attribute."
  [cin cn]
  (let [ccc (get-ccc)
        cin->cn (sci/connector-names-map ccc)]
    (if-not (and (contains? cin->cn cin) (= (get cin->cn cin) cn))
      (update-ccc ccc cin cn)
      {:status  200
       :message "Connector was already in :cloudConnectorClass."})))


(defn conn-add
  "Connector `conn` as request. The resource is in :body."
  [conn]
  (let [cin (conn-inst-name (:body conn))
        cn (-> conn :body :cloudServiceType)
        add-resp (-> conn
                     (assoc :body (complete-connector (:body conn)))
                     conn/add-impl)]
    (if (resp-success? add-resp)
      (add-conn-to-ccc cin cn)
      add-resp)))


(defn conn-edit-or-add
  "Returns response of edit or add operation. Tries to add if edit fails with 404."
  [conn]
  (let [edit-resp (conn/edit-impl conn)]
    ;; adding may fail because template hasn't been registered yet.
    (if (= 404 (:status edit-resp))
      (do
        (log/info "Adding connector" (-> conn :body :id) "for the first time.")
        (conn-add conn))
      edit-resp)))


(defn store-connector
  [conn]
  (->> conn
       (sci/connector-as-request (conn-inst-name conn))
       conn-edit-or-add
       exit-on-resp-error))


;;
;; Generic functions.
;;

(defn store
  [resource]
  (cond
    (configuration? resource) (store-cfg resource)
    (connector? resource) (store-connector resource)))


(defn slurp-and-store
  [f]
  (-> (slurp-edn f)
      validate
      store))


(defn store-to-db
  [files]
  (doseq [f files]
    (slurp-and-store f)))


(defn list-templates
  []
  (log/info "List of accessible templates.")
  (doseq [[ttn ts] *templates*]
    (log/info (format "- templates for '%s'" (name ttn)))
    (doseq [k (keys ts)]
      (log/info k))))


(defn print-template
  [tname]
  (let [ts (apply merge (vals *templates*))
        t (get ts tname)]
    (if (seq t)
      (let [unwanted (into #{} (remove #{:id :cloudServiceType} sci/unwanted-attrs))]
        (log/info (with-out-str (clojure.pprint/pprint (sci/strip-unwanted-attrs t unwanted)))))
      (do
        (log/warn "template" tname "not found.")
        (list-templates)))))


(defn resource-uuid
  [rname]
  (-> rname (str/split #"/") second))


(defn get-configuration-resource
  [rname]
  (sci/get-configuration (resource-uuid rname)))


(defn get-connector-resource
  [rname]
  (sci/get-connector (resource-uuid rname)))


(defn get-resource
  [rname]
  (cond
    (configuration? rname) (get-configuration-resource rname)
    (connector? rname) (get-connector-resource rname)
    :else (log/warn "don't know how to get resource:" rname)))


(defn print-resource
  [r]
  (let [unwanted (into #{} (remove #{:id :cloudServiceType :description} sci/unwanted-attrs))]
    (log/info (with-out-str (clojure.pprint/pprint (sci/strip-unwanted-attrs r unwanted))))))


(defn print-resources
  []
  (doseq [rname *resources*]
    (log/info ";;; Resource:" rname)
    (let [r (get-resource rname)]
      (if (seq r)
        (print-resource r)
        (log/warn "resource" rname "not found.")))))


(defn get-connector-resources
  []
  (-> (conn/query-impl (sci/connector-as-request configuration-resource))
      :body
      :connectors
      :id))


(defn get-configuration-resources
  []
  (-> (cfg/query-impl (sci/configuration-as-request connector-resource))
      :body
      :configurations
      :id))


(defn get-persisted-resources
  [name]
  (cond
    (= name configuration-resource) (get-configuration-resources)
    (= name connector-resource) (get-connector-resources)
    :else (log/warn "don't know how to get resources for" name)))


(defn list-persisted-resources
  [name]
  (doseq [r (get-persisted-resources name)]
    (log/info r)))


(defn list-resources
  []
  (log/info "List of persisted resources.")
  (doseq [r resource-types]
    (log/infof "- resources for '%s'" r)
    (list-persisted-resources r)))


(defn edit-file
  [f kvs]
  (let [kvm (into {}
                  (map
                    #(vec [(keyword (first %)) (second %)])
                    (map #(str/split % #"=") kvs)))]
    (spit-edn (merge (slurp-edn f) kvm) f)))


(defn delete-resource
  [r]
  (cond
    (configuration? r) (sci/delete-config (resource-uuid r))
    (connector? r) (sci/delete-connector (resource-uuid r))
    :else {:status 400
           :body   {:status  400
                    :message (str "Don't know how to delete resource: " r)}}))


(defn delete-resources
  []
  (log/info "Deleting resources from DB:" (str/join ", " *delete-resources*))
  (doseq [r *delete-resources*]
    (let [res (delete-resource r)]
      (if (= 200 (:status res))
        (log/info (format "- %s: Deleted." r))
        (log/warn (format "- %s: Failed deleting: %s" r (-> res :body :message)))))))

;;
;; Command line options processing.
;;

(def cli-options
  [["-b" "--binding BINDING_NS" "Database binding namespace."
    :id :binding-ns
    :default default-db-binding-ns]
   ["-t" "--template TEMPLATE" "Prints out registered template by name."]
   ["-l" "--list" "Lists available templates."]
   ["-p" "--persisted" "Lists resources persisted in DB."]
   ["-r" "--resource RESOURCE" "Prints out persisted resource document(s) by name."
    :id :resources
    :default #{}
    :assoc-fn u/cli-parse-sets]
   ["-e" "--edit-kv KEY=VALUE" (str "Updates or adds key=value in first file from "
                                    "<list-of-files> (other files are ignored).")
    :id :edit-kv
    :default #{}
    :assoc-fn u/cli-parse-sets]
   ["-d" "--delete RESOURCE" "Deletes resource by name."
    :id :delete-resources
    :default #{}
    :assoc-fn u/cli-parse-sets]
   ["-h" "--help"]
   [nil "--logging LEVEL" "Logging level: trace, debug, info, warn, error, fatal, or report."
    :id :level
    :default :info
    :parse-fn #(-> % str/lower-case keyword)]])


(def prog-help
  "
  Ensure :id key with a corresponding resource name is present in each configuration.
  Connector configuration must contain :cloudServiceType key, which corresponds to
  the connector name.

  ES_HOST and ES_PORT should be exported in the environment for the utility
  to be able to connect to Elasticsearch.")


(defn usage
  [options-summary]
  (str/join \newline
            [""
             "Adds or updates different parts of the SlipStream service configuration in DB."
             ""
             "Usage: [options] <list-of-files>"
             ""
             "Options:"
             options-summary
             ""
             "Arguments:"
             "  <list-of-files>    list of edn files with configurations."
             ""
             prog-help]))


(defn -main
  [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]

    (log/set-level! (:level options :info))

    (cond
      (:help options) (u/success (usage summary))
      errors (u/failure (u/error-msg errors)))

    (when (and (not (empty? arguments)) (seq (:edit-kv options)))
      (edit-file (first arguments) (:edit-kv options))
      (u/success))

    ;; for all actions below
    (if-let [binding-ns (:binding-ns options)]
      (init-db-client binding-ns)
      (u/failure "database binding namespace must be specified (-b, --binding)"))

    (when (and (not (empty? arguments)) (empty? (:edit-kv options)))
      (do (init-namespaces)
          (store-to-db arguments))
      (u/success))
    (when (seq (:delete-resources options))
      (alter-var-root #'*delete-resources* (constantly (:delete-resources options)))
      (delete-resources)
      (u/success))
    (when (seq (:resources options))
      (alter-var-root #'*resources* (constantly (:resources options)))
      (print-resources)
      (u/success))
    (when (:list options)
      (init-namespaces)
      (list-templates)
      (u/success))
    (when (not (str/blank? (:template options)))
      (init-namespaces)
      (print-template (:template options)))
    (when (:persisted options)
      (list-resources))))
