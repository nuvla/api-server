(ns sixsq.nuvla.server.resources.module.utils
  (:require
    [clojure.string :as str]))


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
  (assoc resource :parent-path (get-parent-path path)))


