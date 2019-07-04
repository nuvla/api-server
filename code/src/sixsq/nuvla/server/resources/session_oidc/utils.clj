(ns sixsq.nuvla.server.resources.session-oidc.utils
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]))

(defn prefix
  [realm attr]
  (when (and realm attr)
    (str realm ":" attr)))

(defn extract-roles
  [{:keys [realm roles] :as claims}]
  (if (and (not (str/blank? realm)) roles)
    (->> roles
         (remove str/blank?)
         (map (partial prefix realm))
         vec)
    []))

(defn extract-entitlements
  [{:keys [realm entitlement] :as claims}]
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
  [{:keys [realm groups] :as claims}]
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

(defn throw-user-exists [username redirect-url]
  (logu/log-error-and-throw-with-redirect 400 (str "account already exists (" username ")") redirect-url))

(defn throw-invalid-address [ip redirect-url]
  (logu/log-error-and-throw-with-redirect 400 (str "request from invalid IP address (" ip ")") redirect-url))


;; retrieval of configuration parameters

(def oidc-keys #{:client-id :client-secret :public-key :authorize-url :token-url})


(def mitreid-keys #{:client-id :client-secret :public-key :authorize-url :token-url :user-profile-url})


(def mitreid-token-keys #{:client-ips})


(defn config-params
  [prefix key-set redirect-url instance]
  (let [cfg-id (str prefix instance)]
    (try
      (let [config (some-> cfg-id crud/retrieve-by-id-as-admin (select-keys key-set))]
        (if (->> config vals (every? (complement nil?)))
          config
          (throw-bad-client-config cfg-id redirect-url)))
      (catch Exception _
        (throw-bad-client-config cfg-id redirect-url)))))


(def config-oidc-params (partial config-params "configuration/session-oidc-" oidc-keys))


(def config-mitreid-params (partial config-params "configuration/session-mitreid-" mitreid-keys))


(def config-mitreid-token-params (partial config-params "configuration/session-mitreid-token-" mitreid-token-keys))


;; FIXME: Fix ugliness around needing to create ring requests with authentication!
(defn create-callback [baseURI session-id action]
  (let [callback-request {:params   {:resource-name callback/resource-type}
                          :body     {:action         action
                                     :targetResource {:href session-id}}
                          :identity {:current         "INTERNAL"
                                     :authentications {"INTERNAL" {:identity "INTERNAL"
                                                                   :roles    ["ADMIN"]}}}}
        {{:keys [resource-id]} :body status :status} (crud/add callback-request)]
    (if (= 201 status)
      (if-let [callback-resource (crud/set-operations (crud/retrieve-by-id-as-admin resource-id) {})]
        (if-let [validate-op (u/get-op callback-resource "execute")]
          (str baseURI validate-op)
          (let [msg "callback does not have execute operation"]
            (throw (ex-info msg (r/map-response msg 500 resource-id)))))
        (let [msg "cannot retrieve  session callback"]
          (throw (ex-info msg (r/map-response msg 500 resource-id)))))
      (let [msg "cannot create  session callback"]
        (throw (ex-info msg (r/map-response msg 500 session-id)))))))

(defn create-redirect-url
  "Generate a redirect-url from the provided authorizeURL"
  [authorizeURL client-id callback-url]
  (let [url-params-format "?response_type=code&client_id=%s&redirect_uri=%s"]
    (str authorizeURL (format url-params-format client-id callback-url))))

(defn get-mitreid-userinfo
  [userProfileURL access_token]
  (-> (http/get userProfileURL
                {:headers      {"Accept" "application/json"}
                 :query-params {::access_token access_token}})
      :body
      (json/read-str :key-fn keyword)))
