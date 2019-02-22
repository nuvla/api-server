(ns sixsq.nuvla.server.resources.common.dynamic-load
  "Utilities for loading information from CIMI resources dynamically."
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.util.namespace-utils :as dyn]))

(defn resource?
  "If the given symbol represents a resource namespace, the symbol
   is returned; false otherwise.  Resource namespaces have the prefix
   'sixsq.nuvla.server.resources.' and do not contain the
   string 'test'."
  [sym]
  (let [ns-name (name sym)]
    (and
      (or (re-matches #"^sixsq\.nuvla\.server\.resources\.[\w-]+$" ns-name)
          (re-matches #"^sixsq\.nuvla\.connector\.[\w-]+$" ns-name))
      (not (.contains ns-name "test"))
      sym)))

(defn resource-namespaces
  "Returns sequence of the resource namespaces on the classpath."
  []
  (dyn/load-filtered-namespaces resource?))

(defn get-resource-link
  "Returns a vector with the resource type keyword and map with the :href
   keyword associated with the relative URL for the resource. Function returns
   nil if 'resource-type' cannot be found in the resource."
  [resource-ns]
  (if-let [vtag (dyn/resolve "resource-type" resource-ns)]
    [(keyword (deref vtag)) {:href (deref vtag)}]))

(defn- initialize-resource
  "Run a resource's initialization function if it exists."
  [resource-ns]
  (if-let [fvar (dyn/resolve "initialize" resource-ns)]
    (try
      ((deref fvar))
      (log/info "initialized resource" (ns-name resource-ns))
      (catch Exception e
        (log/error "initializing" (ns-name resource-ns) "failed:" (.getMessage e))))))

(defn resource-routes
  "Returns a lazy sequence of all of the routes for resources
   discovered on the classpath."
  []
  (->> (resource-namespaces)
       (map (partial dyn/resolve "routes"))
       (remove nil?)
       (map deref)))

(defn get-resource-links
  "Returns a lazy sequence of all of the resource links for resources
   discovered on the classpath."
  []
  (->> (resource-namespaces)
       (map get-resource-link)
       (remove nil?)))

(defn initialize
  "Runs the initialize function for all resources that define it."
  []
  (doseq [resource-namespace (resource-namespaces)]
    (initialize-resource resource-namespace)))
