(ns com.sixsq.nuvla.server.resources.github.utils
  (:require
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.util.log :as logu]))


(def ^:const github-oath-endpoint "https://github.com/login/oauth/authorize?scope=user:email&client_id=%s&redirect_uri=%s")

(defn throw-bad-client-config [cfg-id redirect-url]
  (logu/log-error-and-throw-with-redirect 500 (str "missing or incorrect configuration (" cfg-id ") for GitHub authentication") redirect-url))

(defn throw-missing-oauth-code [redirect-url]
  (logu/log-error-and-throw-with-redirect 400 "GitHub authentication callback request does not contain required code" redirect-url))

(defn throw-no-access-token [redirect-url]
  (logu/log-error-and-throw-with-redirect 400 "unable to retrieve GitHub access code" redirect-url))

(defn throw-no-user-info [redirect-url]
  (logu/log-error-and-throw-with-redirect 400 "unable to retrieve GitHub user information" redirect-url))

(defn throw-no-matched-user [redirect-url]
  (logu/log-error-and-throw-with-redirect 403 "no matching account for GitHub user" redirect-url))

(defn throw-user-exists [redirect-url]
  (logu/log-error-and-throw-with-redirect 400 "an account with the same email already exists. Have you already registered with email/password?" redirect-url))

(defn config-github-params
  [redirect-url instance]

  (let [cfg-id (str "configuration/session-github-" instance)]
    (try
      (let [{:keys [client-id client-secret]} (crud/retrieve-by-id-as-admin cfg-id)]
        (if (and client-id client-secret)
          [client-id client-secret]
          (throw-bad-client-config cfg-id redirect-url)))
      (catch Exception _
        (throw-bad-client-config cfg-id redirect-url)))))



