(ns sixsq.nuvla.utils.env
  "General utilities for dealing with environment."
  (:require
    [environ.core :as env]))

(def ^:const env-vals-true #{"True" "true" "Yes" "yes" "On" "on" "1" 1})


(defn env-get-as-int
  "Returns environment variable identified by 'e' converted into integer. If
  conversion fails (environment variable is not set, empty or not integer),
  returns 'default' (if provided), otherwise, throws the conversion exception."
  [e & [default]]
  (try
    (Integer/valueOf (env/env e))
    (catch Exception ex
      (or default (throw ex)))))


(defn env-get-as-boolean
  "Returns environment variable identified by 'e' converted into boolean.
  True is returned in case the environment variable 'e' exists and its value is
  the one defined in 'env-vals-true'. Otherwise, false is returned."
  [e]
  (boolean (env-vals-true (env/env e))))
