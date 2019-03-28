(ns sixsq.nuvla.auth.cookies
  "utilities for embedding and extracting tokens in cookies"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils.sign :as sg]
    [sixsq.nuvla.auth.utils.timestamp :as ts]))


(defn revoked-cookie
  "Returns a cookie (as a map) that expires immediately and has 'INVALID' as
   the value. Useful for revoking a cookie in the client's cache. If a name
   is provided, then a map is returned with a single entry with the key being
   the name and the value being the cookie."
  ([]
   {:value "INVALID", :path "/", :max-age 0, :expires (ts/expiry-now-rfc822)})
  ([name]
   {name (revoked-cookie)}))


(defn create-cookie
  "Return a cookie (as a map) that has a token generated from the provided
   info and the default expiration time. If a name is provided, then a map
   is returned with a single entry with the key being the name and the value
   being the cookie."
  ([info]
   (let [timestamp (ts/expiry-later)
         cookie-info (assoc info :exp timestamp)
         token (sg/sign-cookie-info cookie-info)]
     {:value   token
      :secure  true
      :path    "/"
      :expires (ts/rfc822 timestamp)}))
  ([info name]
   {name (create-cookie info)}))

(defn extract-cookie-info
  "Extracts cookie info. Returns nil if no cookie is
   provided or if there is an error when extracting the value from the cookie."
  [{:keys [value] :as cookie}]
  (try
    (when value
      (sg/unsign-cookie-info value))
    (catch Exception e
      (log/warn "Error in extract-cookie-claims: " (str e))
      nil)))
