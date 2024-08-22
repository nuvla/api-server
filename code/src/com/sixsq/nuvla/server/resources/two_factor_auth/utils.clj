(ns com.sixsq.nuvla.server.resources.two-factor-auth.utils
  (:require [clojure.string :as str]
            [com.sixsq.nuvla.auth.password :as auth-password]
            [com.sixsq.nuvla.server.resources.common.utils :as u]
            [com.sixsq.nuvla.server.resources.email.utils :as email-utils]
            [com.sixsq.nuvla.server.util.log :as logu]
            [one-time.core :as ot]))


(def ^:const msg-wrong-2fa-token "Wrong 2FA token")

(def ^:const method-none "none")

(def ^:const method-email "email")

(def ^:const method-totp "totp")

(defmulti send-token (fn [method _user _token] method))

(defmulti generate-token (fn [method _user] method))

(defmulti generate-secret (fn [method _user] method))

(defmulti is-valid-token? (fn [method _request _callback] method))


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
  (format "%06d" (u/secure-rand-int 0 999999)))


(defmethod generate-token method-totp
  [_method _user]
  nil)


(defmethod generate-token method-none
  [_method _user]
  nil)


(defmethod generate-secret :default
  [_method _user]
  nil)


(defmethod generate-secret method-totp
  [_method _user]
  (ot/generate-secret-key))


(defmethod is-valid-token? :default
  [_method {{user-token :token} :body :as _request}
   {{:keys [token]} :data :as _callback}]
  (and
    (not (str/blank? user-token))
    (= user-token token)))


(defmethod is-valid-token? method-totp
  [_method {{user-token :token} :body :as _request}
   {data :data :as _callback}]
  (let [user-token (when (string? user-token)
                     (try
                       (Integer/parseInt user-token)
                       (catch Exception _)))
        secret     (:secret data)]
    (and secret user-token
         (ot/is-valid-totp-token? user-token secret))))
