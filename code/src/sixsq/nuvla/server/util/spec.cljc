(ns sixsq.nuvla.server.util.spec
  "Utilities that provide common spec definition patterns that aren't
   supported directly by the core spec functions and macros."
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [spec-tools.impl :as impl]
    [spec-tools.json-schema :as jsc]
    [spec-tools.parse :as st-parse]
    [spec-tools.visitor :as visitor]))


(def ^:private all-ascii-chars (map str (map char (range 0 256))))


(defn regex-chars
  "Provides a list of ASCII characters that satisfy the regex pattern."
  [pattern]
  (set (filter #(re-matches pattern %) all-ascii-chars)))


(defn merge-kw-lists
  "Merges two lists (or seqs) of namespaced keywords. The results will
   be a sorted vector with duplicates removed."
  [kws1 kws2]
  (vec (sort (set/union (set kws1) (set kws2)))))


(defn merge-keys-specs
  "Merges the given clojure.spec/keys specs provided as a list of maps.
   All the arguments are eval'ed and must evaluate to map constants."
  [map-specs]
  (->> map-specs
       (map eval)
       (apply merge-with merge-kw-lists)))


(defmacro only-keys
  "Creates a closed map definition where only the defined keys are
   permitted. The arguments must be literals, using the same function
   signature as clojure.spec/keys.  (This implementation was provided
   on the clojure mailing list by Alistair Roche.)"
  [& {:keys [req req-un opt opt-un] :as args}]
  `(s/merge (s/keys ~@(apply concat (vec args)))
            (s/map-of ~(set (concat req
                                    (map (comp keyword name) req-un)
                                    opt
                                    (map (comp keyword name) opt-un)))
                      any?)))


(defmacro only-keys-maps
  "Creates a closed map definition from one or more maps that contain
   key specifications as for clojure.spec/keys. All of the arguments
   are eval'ed, so they may be vars containing the definition(s). All
   of the arguments must evaluate to compile-time map constants."
  [& map-specs]
  (let [{:keys [req req-un opt opt-un] :as map-spec} (merge-keys-specs map-specs)]
    `(s/merge (s/keys ~@(apply concat (vec map-spec)))
              (s/map-of ~(set (concat req
                                      (map (comp keyword name) req-un)
                                      opt
                                      (map (comp keyword name) opt-un)))
                        any?))))


(defmacro constrained-map
  "Creates an open map spec using the supplied keys specs with the
   additional constraint that all unspecified entries must match the
   given key and value specs. The keys specs will be evaluated."
  [key-spec value-spec & map-specs]
  (let [{:keys [req req-un opt opt-un] :as map-spec} (merge-keys-specs map-specs)]
    `(s/merge
       (s/every (s/or :attrs (s/tuple ~(set (concat req
                                                    (map (comp keyword name) req-un)
                                                    opt
                                                    (map (comp keyword name) opt-un)))
                                      any?)
                      :link (s/tuple ~key-spec ~value-spec)))
       (s/keys ~@(apply concat (vec map-spec))))))


;;
;; spec parsing patches
;;

(defmethod st-parse/parse-form 'sixsq.nuvla.server.util.spec/only-keys [_dispatch form]
  (st-parse/parse-form 'clojure.spec.alpha/keys form))


(defn transform-form
  [[spec-name & keys-specs]]
  (->> keys-specs
       (merge-keys-specs)
       (apply concat)
       (cons spec-name)))


(defmethod st-parse/parse-form 'sixsq.nuvla.server.util.spec/only-keys-maps [_dispatch form]
  (st-parse/parse-form 'clojure.spec.alpha/keys (transform-form form)))


(defn transform-as-every
  [[spec-name key-spec value-spec & _]]
  [[spec-name key-spec value-spec]])


(defmethod st-parse/parse-form 'sixsq.nuvla.server.util.spec/constrained-map [_dispatch form]
  (st-parse/parse-form 'clojure.spec.alpha/every (transform-as-every form)))


;;
;; patches for spec walking via visitor
;;

(defmethod visitor/visit-spec 'sixsq.nuvla.server.util.spec/only-keys [spec accept options]
  (let [keys (impl/extract-keys (impl/extract-form spec))]
    (accept 'sixsq.nuvla.server.util.spec/only-keys spec (mapv #(visitor/visit % accept options) keys) options)))


(defmethod visitor/visit-spec 'sixsq.nuvla.server.util.spec/only-keys-maps [spec accept options]
  (let [keys (impl/extract-keys (transform-form (impl/extract-form spec)))]
    (accept 'sixsq.nuvla.server.util.spec/only-keys-maps spec (mapv #(visitor/visit % accept options) keys) options)))


(defmethod visitor/visit-spec 'sixsq.nuvla.server.util.spec/constrained-map [spec accept options]
  (let [[_ inner-spec] (transform-as-every (impl/extract-form spec))]
    (accept 'sixsq.nuvla.server.util.spec/constrained-map spec [(visitor/visit inner-spec accept options)] options)))


;; accept-spec monkey patches

(defmethod jsc/accept-spec 'sixsq.nuvla.server.util.spec/only-keys [_dispatch spec children arg]
  (jsc/accept-spec 'clojure.spec.alpha/keys spec children arg))


(defmethod jsc/accept-spec 'sixsq.nuvla.server.util.spec/only-keys-maps [_ spec children _]
  (let [{:keys [req req-un opt opt-un]} (impl/parse-keys (transform-form (impl/extract-form spec)))
        names-un     (map name (concat req-un opt-un))
        names        (map impl/qualified-name (concat req opt))
        required     (map impl/qualified-name req)
        required-un  (map name req-un)
        all-required (not-empty (concat required required-un))]
    (#'jsc/maybe-with-title
      (merge
        {:type       "object"
         :properties (zipmap (concat names names-un) children)}
        (when all-required
          {:required (vec all-required)}))
      spec)))


(defmethod jsc/accept-spec 'sixsq.nuvla.server.util.spec/constrained-map [_ spec children _]
  (#'jsc/maybe-with-title {:type "object", :additionalProperties (impl/unwrap children)} spec))


;;
;; patch transform of spec into ES mapping
;;


(defn- spec-dispatch [dispatch _ _ _] dispatch)


(defmulti accept-spec spec-dispatch :default ::default)


(defmethod accept-spec 'sixsq.nuvla.server.util.spec/only-keys [_dispatch spec children arg]
  (accept-spec 'clojure.spec.alpha/keys spec children arg))


(defmethod accept-spec 'sixsq.nuvla.server.util.spec/only-keys-maps [_dispatch spec children arg]
  (accept-spec 'clojure.spec.alpha/keys spec children arg))


(defmethod accept-spec 'sixsq.nuvla.server.util.spec/constrained-map [_dispatch spec children arg]
  (accept-spec 'clojure.spec.alpha/keys spec children arg))

