(ns com.sixsq.nuvla.server.resources.session-oidc.utils
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.util.general :as gen-util]
    [com.sixsq.nuvla.server.util.log :as logu]))


(defn prefix
  [realm attr]
  (when (and realm attr)
    (str realm ":" attr)))


(defn extract-roles
  [{:keys [realm roles] :as _claims}]
  (if (and (not (str/blank? realm)) roles)
    (->> roles
         (remove str/blank?)
         (map (partial prefix realm))
         vec)
    []))


(defn extract-entitlements
  [{:keys [realm entitlement] :as _claims}]
  (if (and (not (str/blank? realm)) entitlement)
    (let [entitlement (if (instance? String entitlement) [entitlement] entitlement)]
      (->> entitlement
           (remove str/blank?)
           (map (partial prefix realm))
           vec))
    []))


(defn group-hierarchy
  [group]
  (if-not (str/blank? group)
    (let [terms (remove str/blank? (str/split group #"/"))]
      (doall
        (for [i (range 1 (inc (count terms)))]
          (str "/" (str/join "/" (take i terms))))))
    []))


(defn extract-groups
  [{:keys [realm groups] :as _claims}]
  (if (and realm groups)
    (->> groups
         (mapcat group-hierarchy)
         (map (partial prefix realm))
         vec)
    []))


;; exceptions

(defn throw-no-username-or-email [username email redirect-url]
  (logu/log-error-and-throw-with-redirect 400 (str "OIDC/MITREid token is missing name/preferred_name (" username ") or email (" email ")") redirect-url))


(defn throw-no-matched-user [username email redirect-url]
  (logu/log-error-and-throw-with-redirect 400 (str "Unable to match account to name/preferred_name (" username ") or email (" email ")") redirect-url))


;; general exceptions

(defn throw-bad-client-config [cfg-id redirect-url]
  (logu/log-error-and-throw-with-redirect 500 (str "missing or incorrect configuration (" cfg-id ") for OIDC/MITREid authentication") redirect-url))


(defn throw-missing-code [redirect-url]
  (logu/log-error-and-throw-with-redirect 400 "OIDC/MITREid authentication callback request does not contain required code" redirect-url))


(defn throw-no-access-token [redirect-url]
  (logu/log-error-and-throw-with-redirect 400 "unable to retrieve OIDC/MITREid access token" redirect-url))


(defn throw-no-email [redirect-url]
  (logu/log-error-and-throw-with-redirect 400 (str "cannot retrieve OIDC/MITREid primary email") redirect-url))


(defn throw-no-subject [redirect-url]
  (logu/log-error-and-throw-with-redirect 400 (str "OIDC/MITREid token is missing subject (sub) attribute") redirect-url))


(defn throw-invalid-access-code [msg redirect-url]
  (logu/log-error-and-throw-with-redirect 400 (str "error when processing OIDC/MITREid access token: " msg) redirect-url))


(defn throw-inactive-user [username redirect-url]
  (logu/log-error-and-throw-with-redirect 400 (str "account is inactive (" username ")") redirect-url))


(defn throw-user-exists [redirect-url]
  (logu/log-error-and-throw-with-redirect 400 "an account with the same email already exists. Have you already registered with email/password?" redirect-url))


(defn throw-invalid-address [ip redirect-url]
  (logu/log-error-and-throw-with-redirect 400 (str "request from invalid IP address (" ip ")") redirect-url))


;; retrieval of configuration parameters


(defn config-params
  [prefix redirect-url instance]
  (let [cfg-id (str prefix instance)]
    (try
      (let [config (some-> cfg-id crud/retrieve-by-id-as-admin)]
        (if (->> config vals (every? (complement nil?)))
          config
          (throw-bad-client-config cfg-id redirect-url)))
      (catch Exception _
        (throw-bad-client-config cfg-id redirect-url)))))


(def config-oidc-params (partial config-params "configuration/session-oidc-"))

(def config-mitreid-params (partial config-params "configuration/session-mitreid-"))

(def config-mitreid-token-params (partial config-params "configuration/session-mitreid-token-"))


(defn create-redirect-url
  "Generate a redirect-url from the provided authorizeURL"
  ([authorizeURL client-id callback-url]
   (create-redirect-url authorizeURL client-id callback-url nil))
  ([authorizeURL client-id callback-url scope]
   (-> (str authorizeURL "?response_type=code")
       (str "&client_id=" (gen-util/encode-uri-component client-id))
       (cond-> scope (str "&scope=" (gen-util/encode-uri-component scope)))
       (str "&redirect_uri=" callback-url))))


(defn get-mitreid-userinfo
  [userProfileURL access_token]
  (-> (http/get userProfileURL
                {:headers      {"Accept" "application/json"}
                 :query-params {::access_token access_token}})
      :body
      (json/read-str :key-fn keyword)))

(def geant-instance "geant")
