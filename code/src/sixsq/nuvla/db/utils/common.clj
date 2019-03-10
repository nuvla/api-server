(ns sixsq.nuvla.db.utils.common
  "General utilities for dealing with resources."
  (:require
    [clojure.string :as str]))


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
