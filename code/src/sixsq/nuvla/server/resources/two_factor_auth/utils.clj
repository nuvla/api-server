(ns sixsq.nuvla.server.resources.two-factor-auth.utils
  (:require [sixsq.nuvla.server.util.log :as logu]
            [sixsq.nuvla.server.resources.email.utils :as email-utils]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [one-time.core :as ot]
            [sixsq.nuvla.auth.password :as auth-password]
            [clojure.string :as str]))


(def ^:const method-email "email")

(def ^:const method-totp "totp")

(defmulti send-token (fn [method _user _token] method))

(defmulti generate-token (fn [method _user] method))

(defmulti generate-secret (fn [method _user] method))

(defmulti is-valid-token? (fn [_request callback] (-> callback :data :method)))


(defmethod send-token :default
  [method _user _token]
  (logu/log-and-throw-400 (str "Unknown 2FA method: " method)))


(defmethod send-token method-email
  [_method {:keys [id] :as _user} token]
  (if-let [email-address (some-> id auth-password/user-id->email)]
    (email-utils/send-email-token-2fa token email-address)
    (logu/log-and-throw-400 "User should have a validated email.")))


(defmethod send-token method-totp
  [_method _user _token]
  nil)


(defmethod generate-token :default
  [_method _user]
  (format "%04d" (u/secure-rand-int 0 9999)))


(defmethod generate-token method-totp
  [_method _user]
  nil)


(defmethod generate-secret :default
  [_method _user]
  nil)


(defmethod generate-secret method-totp
  [_method _user]
  (ot/generate-secret-key))


(defmethod is-valid-token? :default
  [{{user-token :token} :body :as _request}
   {{:keys [token]} :data :as _callback}]
  (and
    (not (str/blank? user-token))
    (= user-token token)))


(defmethod is-valid-token? method-totp
  [{{user-token :token} :body :as _request}
   {data :data :as _callback}]
  (let [user-token (try
                     (Integer/parseInt user-token)
                     (catch Exception _))
        secret (:secret data)]
    (and
      secret
      (not (str/blank? user-token))
      (ot/is-valid-totp-token? (read-string user-token) secret))))
