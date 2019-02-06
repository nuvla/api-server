(ns sixsq.nuvla.db.utils.common
  "General utilities for dealing with resources."
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [superstring.core :as s]))

;; NOTE: this cannot be replaced with s/lisp-case because it
;; will treat a '/' in a resource name as a word separator.
(defn de-camelcase [str]
  (str/join "-" (map str/lower-case (str/split str #"(?=[A-Z])"))))


(defn- lisp-cased?
  [s]
  (re-matches #"[a-z]+(-[a-z]+)*" s))


(defn lisp-to-camelcase
  "Converts s to CamelCase format.
  s must be lisp-cased, if not empty string is returned."
  [s]
  (if-not (lisp-cased? s)
    (do
      (log/warn s " is not lisp-cased.")
      "")
    (s/pascal-case s)))


(defn split-id
  "Split resource ID into a tuple of [collection docid]. The id is usually in
  the form collection/docid. The CloudEntryPoint is an exception where there is
  no docid, so this will return the collection as the docid: [collection
  collection]. If the input is nil, then the function will return nil."
  [id]
  (when id
    (let [[type docid] (str/split id #"/")]
      [type (or docid type)])))


(defn split-id-kw
  "Same as `split-id` exception that the collection ID will be returned as a
   keyword."
  [id]
  (when id
    (let [[type docid] (str/split id #"/")]
      [(keyword type) (or docid type)])))
