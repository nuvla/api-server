(ns com.sixsq.nuvla.db.filter.parser
  "Implements a parser for CIMI filters as defined in Section 4.1.6.1 of the
  CIMI specification (DSP0263 v1.0.1)."
  (:require
    [clojure.java.io :as io]
    [instaparse.core :as insta]))

;; NOTE: The URL for instaparse must be a string.
(def filter-grammar-url
  (str (io/resource "sixsq/nuvla/db/filter/cimi-filter-grammar.txt")))

(def ^:private cimi-filter-parser
  (insta/parser filter-grammar-url))

(defn parse-cimi-filter
  [s]
  (insta/parse cimi-filter-parser s))
