(ns sixsq.nuvla.db.es.query)

(defn bool-query
  [type-occurrence clause]
  {:bool {type-occurrence clause}})

(defn and-clauses
  [clauses]
  (bool-query :filter clauses))

(defn or-clauses
  [clauses]
  (bool-query :should clauses))

(defn not-clause
  [clause]
  (bool-query :must_not clause))

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

(defn term-query
  [term value]
  {:term {term value}})

(defn terms-query
  [term value]
  {:terms {term value}})

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

(defn geo-shape
  [term op value]
  {:geo_shape {term {:shape    value
                     :relation op}}})

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
