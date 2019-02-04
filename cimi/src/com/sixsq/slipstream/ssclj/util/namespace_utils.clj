(ns com.sixsq.slipstream.ssclj.util.namespace-utils
  "Utilities for dynamic loading of namespaces and vars."
  (:refer-clojure :exclude [resolve])
  (:require
    [clojure.java.classpath :as cp]
    [clojure.tools.logging :as log]
    [clojure.tools.namespace.find :as nsf]))

(defn filter-namespaces
  "Returns symbols for all of the namespaces that match the given filter
   function."
  [f]
  (->> (cp/classpath)
       (nsf/find-namespaces)
       (filter f)))

(defn load-namespace
  "Dynamically loads the given namespace, returning the namespace.
   Will return nil if the namespace could not be loaded."
  [ns-sym]
  (try
    (require ns-sym)
    (log/info "loaded namespace:" ns-sym)
    (find-ns ns-sym)
    (catch Exception e
      (log/error "could not load namespace:" ns-sym " ===>>> " (.getMessage e))
      nil)))

(defn load-filtered-namespaces
  "Returns a sequence of the requested namespaces on the classpath."
  [f]
  (->> f
       (filter-namespaces)
       (map load-namespace)
       (remove nil?)))

(defn resolve
  "Retrieves the named var in the given namespace, returning
   nil if the var could not be found.  Function logs the success or
   failure of the request.  The argument order is reverse from the
   usual 'resolve' function to allow for thread-last forms."
  [varname resource-ns]

  (let [v (ns-resolve resource-ns (symbol varname))]
    (if v
      (log/debug varname "found in" (ns-name resource-ns))
      (log/debug varname "NOT found in" (ns-name resource-ns)))
    v))

