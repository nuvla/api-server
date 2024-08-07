(ns com.sixsq.nuvla.server.resources.common.event-context
  (:require [com.sixsq.nuvla.server.util.time :as t]))


(def ^:dynamic *context* nil)


(defmacro with-context
  "Initializes an event context map.
   Information can be added to the context via `add-to-context`.
   The context can be retrieved via `get-context`."
  [& body]
  `(binding [*context* (atom {:timestamp (t/now-str)})]
     ~@body))


(defn get-context
  "Returns the current context."
  []
  (some-> *context* deref))


(defn add-to-context
  "Adds value `v` to the current context under key `k`."
  [k v]
  (when (get-context)
    (swap! *context* assoc k v)))

(defn add-to-visible-to
  "Adds `v` to visible to"
  [& claims]
  (when @*context*
    (swap! *context* update :visible-to concat claims)))


(defn add-linked-identifier
  "Adds the identifier of a linked entity to the context."
  [id]
  (when (and (some? id) @*context*)
    (swap! *context* update :linked-identifiers conj id)))
