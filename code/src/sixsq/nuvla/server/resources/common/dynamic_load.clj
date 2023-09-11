(ns sixsq.nuvla.server.resources.common.dynamic-load
  "Utilities for loading information from CIMI resources dynamically."
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.util.namespace-utils :as ns-util]))


(defn resource?
  "If the given symbol represents a resource namespace, the symbol
   is returned; false otherwise.  Resource namespaces have the prefix
   'sixsq.nuvla.server.resources.' and do not contain the
   string 'test'."
  [sym]
  (let [ns-name (name sym)]
    (and
      (re-matches #"^sixsq\.nuvla\.server\.resources\.[\w-]+$" ns-name)
      (not (.contains ns-name "test"))
      sym)))


(defn resource-namespaces
  "Returns sequence of the resource namespaces on the classpath."
  []
  (ns-util/load-filtered-namespaces resource?))


(defn get-resource-link
  "Returns a vector with the resource type keyword and map with the :href
   keyword associated with the relative URL for the resource. Function returns
   nil if 'resource-type' cannot be found in the resource."
  [resource-ns]
  (when-let [vtag (ns-util/resolve "resource-type" resource-ns)]
    [(keyword (deref vtag)) {:href (deref vtag)}]))


(def ^:private initialize-data-fns (atom []))


(defn cache-initialize-data-fn
  [resource-ns]
  (when-let [fvar (ns-util/resolve "initialize-data" resource-ns)]
    (log/debug (str "caching data init function: " fvar))
    (swap! initialize-data-fns conj (deref fvar))))


(defn- initialize-resource
  "Run a resource's initialization function if it exists.
  Collect data initialization function for future use in re-initialization."
  [resource-ns]
  (when-let [fvar (ns-util/resolve "initialize" resource-ns)]
    (try
      ((deref fvar))
      (log/info "initialized resource" (ns-name resource-ns))
      (cache-initialize-data-fn resource-ns)
      (catch Exception e
        (log/error "initializing" (ns-name resource-ns) "failed:" (.getMessage e))))))


(defn resource-routes
  "Returns a lazy sequence of all of the routes for resources
   discovered on the classpath."
  []
  (->> (resource-namespaces)
       (map (partial ns-util/resolve "routes"))
       (remove nil?)
       (map deref)))


(defn get-resource-links
  "Returns a lazy sequence of all of the resource links for resources
   discovered on the classpath."
  []
  (->> (resource-namespaces)
       (map get-resource-link)
       (remove nil?)))


(defn initialize-data
  "Helper function. Runs previously cached initialize-data functions to
  populate the DB with the required default data."
  []
  (doseq [f @initialize-data-fns]
    (try
      (f)
      (log/debug "initialized data for resource via " f)
      (catch Exception e
        (log/error "initializing data for resource via " f " failed:" (.getMessage e))))))


(defn initialize
  "Runs the initialize function for all resources that define it."
  []
  (doseq [resource-namespace (sort-by ns-util/initialization-order
                                      (resource-namespaces))]
    (log/debug "Initializing resource " resource-namespace)
    (initialize-resource resource-namespace)))
