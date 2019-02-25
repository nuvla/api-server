(ns sixsq.nuvla.db.es.query
  (:refer-clojure :exclude [and or]))


(defn prefix
  [term prefix]
  {:prefix {term prefix}})


(defn full-text-search
  [term value]
  {:simple_query_string {:query  value
                         :fields [term]}})


(defn exists
  [term]
  {:exists {:field term}})


(defn missing
  [term]
  {:bool {:must_not (exists term)}})


(defn eq
  [term value]
  {:term {term value}})


(defn ne
  [term value]
  {:bool {:must_not (eq term value)}})


(defn gte
  [term value]
  {:range {term {:gte value}}})


(defn gt
  [term value]
  {:range {term {:gt value}}})


(defn lte
  [term value]
  {:range {term {:lte value}}})


(defn lt
  [term value]
  {:range {term {:lt value}}})


(defn and
  [clauses]
  {:bool {:filter (vec clauses)}})


(defn or
  [clauses]
  {:bool {:should (vec clauses)}})


(defn constant-score-query
  [filter]
  {:constant_score
   {:filter filter
    :boost  1.0}})


(defn match-all-query
  []
  {:match_all {:boost 1.0}})


(defn match-none-query
  []
  {:query {:match_none {}}})
