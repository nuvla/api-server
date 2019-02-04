(ns com.sixsq.slipstream.auth.github
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]))


(defn parse-github-user
  [user-info]
  (-> user-info
      :body
      (json/read-str :key-fn keyword)
      (select-keys [:login :email])))


(defn primary-or-verified
  "Return primary verified email (if found) otherwise fallback to any (non
   deterministic) verified email."
  [emails]
  (let [verified (filter :verified emails)]
    (if-let [primary (first (filter :primary verified))]
      (:email primary)
      (:email (first verified)))))


(defn retrieve-private-email
  [access-token]
  (let [user-emails-response (http/get "https://api.github.com/user/emails"
                                       {:headers {"Authorization" (str "token " access-token)}})
        user-emails (-> user-emails-response :body (json/read-str :key-fn keyword))]
    (primary-or-verified user-emails)))


(defn retrieve-email
  [user-info access-token]
  (if-let [public-email (:email user-info)]
    public-email
    (retrieve-private-email access-token)))


(defn get-github-access-token
  [client-id client-secret oauth-code]
  (-> (http/post "https://github.com/login/oauth/access_token"
                 {:headers     {"Accept" "application/json"}
                  :form-params {:client_id     client-id
                                :client_secret client-secret
                                :code          oauth-code}})
      :body
      (json/read-str :key-fn keyword)
      :access_token))


(defn get-github-user-info
  [access-token]
  (parse-github-user
    (http/get "https://api.github.com/user"
              {:headers {"Authorization" (str "token " access-token)}})))
