(ns sixsq.nuvla.server.resources.module.utils
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.util.response :as r]))

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


(defn retrieve-content-id
  [versions index]
  (let [index (or index (last-index versions))]
    (-> versions (nth index) :href)))


(defn split-uuid
  [uuid]
  (let [[uuid-module index] (str/split uuid #"_")
        index (some-> index edn/read-string)]
    [uuid-module index]))


(defn get-module-content
  [{:keys [id versions] :as module-meta} uuid]
  (let [version-index  (second (split-uuid uuid))
        version-id     (retrieve-content-id versions version-index)
        module-content (if version-id
                         (-> version-id
                             (crud/retrieve-by-id-as-admin)
                             (dissoc :resource-type :operations :acl))
                         (when version-index
                           (throw (r/ex-not-found
                                    (str "Module version not found: " id)))))]
    (assoc module-meta :content module-content)))