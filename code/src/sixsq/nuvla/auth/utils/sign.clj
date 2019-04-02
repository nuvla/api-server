(ns sixsq.nuvla.auth.utils.sign
  (:require
    [buddy.sign.jws :as jws]
    [buddy.sign.jwt :as jwt]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils.certs :as certs]))


(def default-algorithm :rs256)


(defn sign-cookie-info
  [cookie-info]
  (jwt/sign cookie-info (certs/private-key :nuvla-session-key) {:alg default-algorithm}))


(defn algorithm-option
  [token]
  (try
    {:alg (-> token jws/decode-header :alg (or default-algorithm))}
    (catch Exception _
      (log/warn "exception when processing JWT header; using default algorithm" default-algorithm)
      {:alg default-algorithm})))


(defn unsign-cookie-info
  "If passed only the token from which to extract the cookie info, then the public
   key in the path defined by the keyword :nuvla-session-crt will be used to
   verify the cookie info. If a second keyword argument is provided, it will be used
   to find the public key path. If the second argument is a string, it is
   treated as a raw public key string and will be used directly."
  ([token]
   (unsign-cookie-info token :nuvla-session-crt))
  ([token env-var-kw-or-cert-string]
   (let [options (algorithm-option token)
         public-key (if (keyword? env-var-kw-or-cert-string)
                      (certs/public-key env-var-kw-or-cert-string)
                      (certs/str->public-key env-var-kw-or-cert-string))]
     (jwt/unsign token public-key options))))
