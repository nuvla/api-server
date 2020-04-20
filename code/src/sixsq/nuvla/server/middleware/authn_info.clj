(ns sixsq.nuvla.server.middleware.authn-info
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.cookies :as cookies]
    [clojure.set :as set]))


;; NOTE: ring uses lower-cased values of header names!
(def ^:const authn-info-header
  "nuvla-authn-info")


(def ^:const authn-cookie
  "com.sixsq.nuvla.cookie")


(defn parse-authn-header
  [request]
  (seq (remove str/blank? (-> request
                              (get-in [:headers authn-info-header])
                              (or "")
                              (str/split #"\s+")))))


(defn is-session?
  "returns nil if the value does not look like a session; the session otherwise"
  [^String s]
  (if s
    (re-matches #"^session/.*" s)))


(defn extract-header-authn-info
  [request]
  (when-let [terms (parse-authn-header request)]
    (let [user-id (first terms)
          claims  (seq (rest terms))
          session (first (keep is-session? (rest terms)))]
      (cond-> {:claims (set [user-id "group/nuvla-anon"])}
              user-id (assoc :user-id user-id)
              claims (update :claims set/union (set claims))
              session (assoc :session session)))))

(defn split-claims
  [claims-str]
  (set (remove str/blank? (-> claims-str
                              (or "")
                              (str/split #"\s+")))))


(defn cookie-info->authn-info
  "Returns a tuple with the user-id and list of claims based on the
   provided cookie info map."
  [{:keys [user-id active-claim claims session]}]
  (when user-id
    (let [claims (split-claims claims)]
      (cond-> {}
              user-id (assoc :user-id (or active-claim user-id))
              claims (assoc :claims claims)
              session (assoc :session session)))))


(defn extract-cookie-authn-info
  [{:keys [cookies] :as request}]
  (some-> cookies
          (get authn-cookie)
          (cookies/extract-cookie-info)
          (cookie-info->authn-info)))


(defn add-anon-role [{:keys [nuvla/authn] :as request}]
  (let [{:keys [claims]} authn
        claims-updated (conj (set claims) "group/nuvla-anon")]
    (assoc-in request [:nuvla/authn :claims] claims-updated)))


(defn add-authn-info
  [request]
  (if-let [authn-info (or (extract-header-authn-info request)
                          (extract-cookie-authn-info request))]
    (assoc request :nuvla/authn authn-info)
    request))


(defn wrap-authn-info
  "Middleware that adds an identity map to the request based on information in
   the nuvla-authn-info header or authentication cookie. If both are provided,
   the header takes precedence."
  [handler]
  (fn [request]
    (-> request
        add-authn-info
        add-anon-role
        handler)))
