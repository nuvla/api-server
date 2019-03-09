(ns sixsq.nuvla.server.middleware.cimi-params.utils
  "Utilities for transforming the raw CIMI parameter values into to validated,
   formatted values for further processing."
  (:require
    [clojure.string :as str]
    [instaparse.core :as insta]
    [sixsq.nuvla.server.utils :as r]))

(defn as-vector
  "Ensures that the given argument is a vector, coercing the given value if
   necessary. Vectors and lists are returned as a vector. Nil returns an empty
   vector. All other values will be wrapped into a 1-element vector."
  [arg]
  (cond
    (nil? arg) []
    (vector? arg) arg
    (list? arg) (vec arg)
    :else [arg]))

(defn as-long
  "Coerce the value into a long. The value can either be a string or a long."
  [s]
  (let [s (str s)]
    (try
      (Long/parseLong ^String s)
      (catch NumberFormatException _
        nil))))

(defn first-valid-long
  "In a vector of strings or numbers, this extracts the first value that can
   be coerced into a valid long."
  [v]
  (->> v
       (as-vector)
       (map as-long)
       (remove nil?)
       first))

(defn wrap-join-with-and
  "Wraps individual filters in parentheses and then combines them with
   a logical AND."
  [filters]
  (str/join " and " (map #(str "(" % ")") filters)))

(defn throw-illegal-for-invalid-filter
  "Checks if the parse result is marked as a failure. If so, an exception is
   raised. If not, the original value is returned."
  [parse-result]
  (if (insta/failure? parse-result)
    (throw (r/ex-bad-CIMI-filter (insta/get-failure parse-result)))
    parse-result))

(defn comma-split
  "Split string on commas, optionally surrounded by whitespace.  All values
  have whitespace trimmed from both ends. Nil values and empty strings are
  removed from the output.  Passing nil returns an empty vector."
  [^String s]
  (if s
    (->> (str/split s #"\s*,\s*")
         (remove nil?)
         (map str/trim)
         (remove str/blank?))
    []))

(defn reduce-expand-set
  "Reduce the given set to :all if the set contains the wildcard '*' or to
  :none if the set is empty or nil. Otherwise this returns the original set."
  [key-set]
  (cond
    (contains? key-set "*") :all
    (empty? key-set) :none
    :else key-set))

(defn reduce-select-set
  "Reduce the given set to nil if the set contains the wildcard '*'. If the
   wildcard is not present, then the initial key set will be returned (which
   may also be nil)."
  [key-set]
  (when-not (contains? key-set "*")
    key-set))

(defn orderby-clause
  "Splits an orderby clause value at the colon and then returns a tuple
  [attribute name, direction] where the direction is either :asc (default) or
  :desc. If the attribute name is blank or not valid, then nil will be
  returned."
  [s]
  (let [[attr order] (->> s
                          (re-matches #"^(.*?)(?::(asc|desc))?$")
                          rest
                          (remove nil?)
                          (map str/trim))]
    (when-not (str/blank? attr)
      [attr (or (keyword order) :asc)])))

(defn aggregation-clause
  "Splits a aggregation clause value at the first colon and then returns
  [algorithm name, attribute name] where the algorithm name is a keyword. If
  the value is not valid (e.g. does not contain an algorithm name, then nil
  will be returned."
  [s]
  (let [[algo attr] (->> s
                         (re-matches #"([a-z_-]+):(\S+)")
                         rest
                         (map str/trim))]
    (when-not (str/blank? attr)
      [(keyword algo) attr])))
