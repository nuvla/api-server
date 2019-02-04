(ns com.sixsq.slipstream.auth.env-fixture
  (:require
    [clojure.java.io :as io]
    [environ.core :as env]))

(def env-authn {"AUTH_PRIVATE_KEY" (io/resource "auth_privkey.pem")
                "AUTH_PUBLIC_KEY"  (io/resource "auth_pubkey.pem")})

(def env-map (into {} (map (fn [[k v]] [(#'env/keywordize k) v]) env-authn)))

