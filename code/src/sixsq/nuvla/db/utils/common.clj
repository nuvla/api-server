(ns sixsq.nuvla.db.utils.common
  "General utilities for dealing with resources."
  (:require
    [clojure.string :as str]
    [environ.core :as env]))


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


(defn env-get-as-int
  "Returns environment variable identified by 'e' converted into integer. If
  conversion fails (environment variable is not set, empty or not integer),
  returns 'default' (if provided), otherwise, throws the conversion exception."
  [e & [default]]
  (try
    (Integer/valueOf (env/env e))
    (catch Exception ex
      (or default (throw ex)))))
