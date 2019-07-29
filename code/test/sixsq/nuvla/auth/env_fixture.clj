(ns sixsq.nuvla.auth.env-fixture
  (:require
    [clojure.java.io :as io]
    [environ.core :as env]))

(def env-authn {"NUVLA_SESSION_KEY" (io/resource "session.key")
                "NUVLA_SESSION_CRT" (io/resource "session.crt")})

(def env-map (into {} (map (fn [[k v]] [(#'env/keywordize k) v]) env-authn)))

