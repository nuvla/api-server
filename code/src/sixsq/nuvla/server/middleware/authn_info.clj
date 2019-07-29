(ns sixsq.nuvla.server.middleware.authn-info
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.cookies :as cookies]))


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
      (cond-> {}
              user-id (assoc :user-id user-id)
              claims (assoc :claims (set claims))
              session (assoc :session session)))))


(defn cookie-info->authn-info
  "Returns a tuple with the user-id and list of claims based on the
   provided cookie info map."
  [{:keys [user-id claims session]}]
  (when user-id
    (let [claims (set (remove str/blank? (-> claims
                                             (or "")
                                             (str/split #"\s+")
                                             (conj session))))]
      (cond-> {}
              user-id (assoc :user-id user-id)
              claims (assoc :claims (set claims))
              session (assoc :session session)))))


(defn extract-cookie-authn-info
  [{:keys [cookies] :as request}]
  (some-> cookies
          (get authn-cookie)
          (cookies/extract-cookie-info)
          (cookie-info->authn-info)))


(defn add-anon-role-and-user [{:keys [nuvla/authn] :as request}]
  (let [{:keys [user-id claims]} authn
        claims-updated (cond-> (conj (set claims) "group/nuvla-anon")
                               user-id (conj user-id))]
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
        add-anon-role-and-user
        handler)))
