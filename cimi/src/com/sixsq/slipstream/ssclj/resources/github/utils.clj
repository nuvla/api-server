(ns com.sixsq.slipstream.ssclj.resources.github.utils
  (:require [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
            [com.sixsq.slipstream.ssclj.util.log :as logu]))


(def ^:const github-oath-endpoint "https://github.com/login/oauth/authorize?scope=user:email&client_id=%s&redirect_uri=%s")

(defn throw-bad-client-config [cfg-id redirectURI]
  (logu/log-error-and-throw-with-redirect 500 (str "missing or incorrect configuration (" cfg-id ") for GitHub authentication") redirectURI))

(defn throw-missing-oauth-code [redirectURI]
  (logu/log-error-and-throw-with-redirect 400 "GitHub authentication callback request does not contain required code" redirectURI))

(defn throw-no-access-token [redirectURI]
  (logu/log-error-and-throw-with-redirect 400 "unable to retrieve GitHub access code" redirectURI))

(defn throw-no-user-info [redirectURI]
  (logu/log-error-and-throw-with-redirect 400 "unable to retrieve GitHub user information" redirectURI))

(defn throw-no-matched-user [redirectURI]
  (logu/log-error-and-throw-with-redirect 403 "no matching account for GitHub user" redirectURI))

(defn throw-user-exists [username redirectURI]
  (logu/log-error-and-throw-with-redirect 400 (str "account already exists (" username ")") redirectURI))

(defn config-github-params
  [redirectURI instance]

  (let [cfg-id (str "configuration/session-github-" instance)]
    (try
      (let [{:keys [clientID clientSecret]} (crud/retrieve-by-id-as-admin cfg-id)]
        (if (and clientID clientSecret)
          [clientID clientSecret]
          (throw-bad-client-config cfg-id redirectURI)))
      (catch Exception _
        (throw-bad-client-config cfg-id redirectURI)))))



