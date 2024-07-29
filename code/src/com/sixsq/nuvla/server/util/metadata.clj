(ns com.sixsq.nuvla.server.util.metadata
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.utils.acl :as acl-utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as resource-metadata]
    [spec-tools.json-schema :as jsc])
  (:import
    (clojure.lang Namespace)))


(declare select-resource-metadata-keys)


(defn treat-array
  [{:keys [items] :as _description}]
  (when (seq items)
    ;; items is a single attribute map, not a collection
    [(select-resource-metadata-keys ["item" items])]))


(defn treat-map
  [{:keys [properties] :as _description}]
  (when (seq properties)
    (->> properties
         (map select-resource-metadata-keys)
         (remove nil?)
         vec)))


(defn treat-children
  [{:keys [type] :as description}]
  (case type
    "array" (treat-array description)
    "map" (treat-map description)
    "geo-point" (treat-map description)
    "geo-shape" (treat-map description)
    nil))


(def ^:const metadata-key-defaults
  {:server-managed false
   :editable       true
   :section        "data"
   :hidden         false
   :sensitive      false
   :indexed        true})


(defn select-resource-metadata-keys
  [[attribute-name {:keys [value-scope fulltext] :as description}]]
  (let [{:keys [name display-name] :as desc}
        (select-keys description #{:name :type
                                   :server-managed :required :editable
                                   :display-name :description :help
                                   :section :order :hidden :sensitive
                                   :indexed :fulltext})
        child-types (treat-children description)]
    (cond-> (merge metadata-key-defaults desc)
            (nil? name) (assoc :name attribute-name)
            (nil? display-name) (assoc :display-name (or name attribute-name))
            (and fulltext (#{"string" "uri" "resource-id"} type)) (assoc :fulltext fulltext)
            value-scope (assoc :value-scope value-scope)
            child-types (assoc :child-types child-types))))


(defn generate-attributes
  "generate the attributes and value-scope fields of the resource metadata
   from the schema definition"
  [spec]
  (let [json       (jsc/transform spec)

        required   (:required json)

        attributes (->> json
                        :properties
                        (map select-resource-metadata-keys)
                        (sort-by :name)
                        vec)]

    (if (seq attributes)
      (cond-> {:attributes attributes}
              required (assoc :required required))
      {})))


(defn get-doc
  "Extracts the namespace documentation provided in the namespace declaration."
  [resource-ns]
  (-> resource-ns meta :doc))


(defn get-actions
  [resource-ns]
  (some-> resource-ns
          (ns-resolve 'actions)
          deref))


(defn get-capabilities
  [resource-ns]
  (some-> resource-ns
          (ns-resolve 'capabilities)
          deref))


(defn as-namespace
  [ns]
  (when ns
    (cond
      (instance? Namespace ns) ns
      (symbol? ns) (find-ns (symbol (namespace ns)))
      (keyword? ns) (find-ns (symbol (namespace ns)))
      (string? ns) (find-ns (symbol ns)))))


(defn ns->type-uri
  "Uses the last term of the resource's namespace as the type-uri. For a
   normal resource this is the same as the 'resource-url' value. This will be
   different for resources with subtypes. The argument can be any value that
   can be converted to a namespace with 'as-namespace'."
  [ns]
  (-> ns as-namespace str (str/split #"\.") last))


(defn generate-metadata
  "Generate the resource-metadata from the provided namespace"
  ([parent-ns spec]
   (generate-metadata nil parent-ns spec nil))
  ([child-ns parent-ns spec]
   (generate-metadata child-ns parent-ns spec nil))
  ([child-ns parent-ns spec suffix]
   (if-let [parent-ns (as-namespace parent-ns)]
     (let [child-ns      (as-namespace child-ns)

           resource-name (cond-> (ns->type-uri (or child-ns parent-ns))
                                 suffix (str " \u2014 " suffix))

           doc           (get-doc (or child-ns parent-ns))
           type-uri      (cond-> (ns->type-uri (or child-ns parent-ns))
                                 suffix (str "-" suffix))

           common        {:id            "resource-metadata/dummy-id"
                          :created       "1964-08-25T10:00:00.00Z"
                          :updated       "1964-08-25T10:00:00.00Z"
                          :resource-type resource-metadata/resource-type
                          :acl           (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                                                   :view-acl ["group/nuvla-anon"]})
                          :type-uri      type-uri
                          :name          resource-name
                          :description   doc}

           attributes    (generate-attributes spec)

           actions       (get-actions parent-ns)

           capabilities  (get-capabilities parent-ns)]

       (if (and doc spec type-uri)
         (cond-> common
                 attributes (merge attributes)
                 (seq actions) (assoc :actions actions)
                 (seq capabilities) (assoc :capabilities capabilities))
         (log/error "namespace documentation and spec cannot be null for" (str parent-ns))))
     (log/error "cannot find namespace for" (str parent-ns)))))
