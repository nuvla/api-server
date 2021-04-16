(ns sixsq.nuvla.server.resources.module.utils
  (:require
    [clj-yaml.core :as yaml]
    [clojure.set :as set]
    [clojure.string :as str]
    [sixsq.nuvla.server.util.log :as logu]))

(def ^:const subtype-comp "component")

(def ^:const subtype-app "application")

(def ^:const subtype-app-k8s "application_kubernetes")

(def ^:const subtype-project "project")


(defn is-application?
  [subtype]
  (= subtype subtype-app))

(defn is-application-k8s?
  [subtype]
  (= subtype subtype-app-k8s))

(defn is-component?
  [subtype]
  (= subtype subtype-comp))

(defn is-project?
  [subtype]
  (= subtype subtype-project))


(defn split-resource
  "Splits a module resource into its metadata and content, returning the tuple
   [metadata, content]."
  [{:keys [content] :as body}]
  (let [module-meta (dissoc body :content)]
    [module-meta content]))


(defn get-parent-path
  "Gets the parent path for the given path. The root parent is the empty
   string."
  [path]
  (when path (str/join "/" (-> path (str/split #"/") drop-last))))


(defn set-parent-path
  "Updates the :parent-path key in the module resource to ensure that it is
   consistent with the value of :path."
  [{:keys [path] :as resource}]
  (cond-> resource
          path (assoc :parent-path (get-parent-path path))))


(defn set-published
  "Updates the :parent-path key in the module resource to ensure that it is
   consistent with the value of :path."
  [{:keys [versions] :as resource}]
  (cond-> resource
          (not (is-project? resource)) (assoc :published (boolean (some :published versions)))))


(defn last-index
  [versions]
  (loop [i (dec (count versions))]
    (when (not (neg? i))
      (if (some? (nth versions i))
       i
       (recur (dec i))))))


(def ^:const compose-specific-keys
  #{"devices" "build" "cap_add" "cap_drop" "cgroup_parent" "container_name" "depends_on"
    "external_links" "network_mode" "restart" "security_opt" "tmpfs" "userns_mode" "privileged"
    "domainname" "ipc" "mac_address" "shm_size"})


(def ^:const swarm-specific-keys #{"configs" "deploy"})


(defn docker-compose-services-keys-set
  [{:strs [services] :as docker-compose}]
  (set
    (when (coll? services)
      (->> services
           (map (fn [[_ service-content]]
                  (when (map? service-content)
                    (-> service-content keys set))))
           (reduce set/union)))))


(defn list-swarm-unsupported-options
  [services-keys-set]
  (set/intersection services-keys-set compose-specific-keys))


(defn some-services-has-swarm-options?
  [services-keys-set]
  (boolean (some swarm-specific-keys services-keys-set)))

(defn parse-and-throw-when-not-parsable-docker-compose
  [docker-compose]
  (try
    (yaml/parse-string docker-compose :keywords false)
    (catch Exception _
      (logu/log-and-throw-400 "Server wasn't able to parse docker-compose yaml!"))))


(defn get-compatibility-fields
  [docker-compose]
  (let [services-keys-set   (docker-compose-services-keys-set docker-compose)
        unsupported-options (-> services-keys-set list-swarm-unsupported-options vec)]
    (if (and (not (some-services-has-swarm-options? services-keys-set))
             (seq unsupported-options))
      ["docker-compose" []]
      ["swarm" unsupported-options])))


(defn parse-get-compatibility-fields
  [subtype docker-compose]
  (when (is-application? subtype)
    (-> docker-compose
        parse-and-throw-when-not-parsable-docker-compose
        get-compatibility-fields)))
