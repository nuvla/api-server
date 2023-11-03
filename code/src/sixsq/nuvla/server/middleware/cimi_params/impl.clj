(ns sixsq.nuvla.server.middleware.cimi-params.impl
  "Provides functions that transform CIMI query parameters specified in a
   request into validated, reformatted values that facilitate processing at the
   database layer."
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.middleware.cimi-params.mime :as mime]
    [sixsq.nuvla.server.middleware.cimi-params.utils :as utils]))

(defn cimi-first
  "Calculates the value for the :first key in the CIMI parameters map. If a
   valid value isn't specified, this defaults to 1."
  [{:keys [first] :as _params}]
  (or (utils/first-valid-long first) 1))

(defn cimi-last
  "Calculates the value for the :last key in the CIMI parameters map. If a
   valid value isn't specified, then the value is nil."
  [{:keys [last] :as _params}]
  (utils/first-valid-long last))

(defn cimi-filter
  "Calculates the :filter key for the CIMI parameters map; the value is the
  AST resulting from the parsing of the complete filter. If the filter
  parameter appears more than once, then the filters are combined with a
  logical AND. If the filter is invalid, then an exception is thrown."
  [{:keys [filter] :as _params}]
  (some->> filter
           utils/as-vector
           utils/wrap-join-with-and
           parser/parse-cimi-filter
           utils/throw-illegal-for-invalid-filter))

(defn cimi-select
  "Calculates the value for the :select key in the CIMI parameters map. The
   value will be nil if the select key was not specified. Otherwise a set of the
   desired keys is returned."
  [{:keys [select] :as _params}]
  (some->> select
           utils/as-vector
           (mapcat utils/comma-split)
           set))

(defn cimi-format
  "Calculates the value for the :format key in the CIMI parameters map. The
   processing of the format parameter is more lenient than the CIMI
   specification in the following ways:

    - The first _acceptable_ value is used, rather than strictly the first.
    - Surrounding whitespace is removed from values before processing.

  In addition to the 'json' and 'xml' values in the specification, this also
  accepts 'edn'.

  Note that the specification states that this option must override any values
  provided in the HTTP Accept header. Consequently, the value must be checked
  before generating the output."
  [{:keys [format] :as _params}]
  (->> format
       utils/as-vector
       (filter string?)
       (map str/trim)
       (map str/lower-case)
       (filter mime/accepted-formats)
       (map mime/accepted-mime-types)
       first))

(defn cimi-orderby
  "Calculates the value of the :orderby parameter in the CIMI parameters map.
  The value of the :orderby key will be an empty list if the parameter isn't
  specified or has no valid values. Otherwise it will contain a sequence of
  [attribute name, direction] tuples where the direction is either :asc
  (ascending) or :desc (descending)."
  [{:keys [orderby] :as _params}]
  (->> orderby
       utils/as-vector
       (mapcat utils/comma-split)
       (map utils/orderby-clause)
       (remove nil?)))

(defn cimi-aggregation
  "Calculates the value of the :aggregation key for the CIMI parameters map.
  The value is a map where the keys are algorithm names (as keywords) and the
  values are the attribute names to which the algorithms should be applied."
  [{:keys [aggregation] :as _params}]
  (->> aggregation
       utils/as-vector
       (mapcat utils/comma-split)
       (map utils/aggregation-clause)
       (remove nil?)))
