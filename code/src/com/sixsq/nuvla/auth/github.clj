(ns com.sixsq.nuvla.auth.github
  (:require
    [clj-http.client :as http]
    [jsonista.core :as j]))


(defn parse-github-user
  [user-info]
  (-> user-info
      :body
      (j/read-value j/keyword-keys-object-mapper)
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
        user-emails          (-> user-emails-response :body (j/read-value j/keyword-keys-object-mapper))]
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
      (j/read-value j/keyword-keys-object-mapper)
      :access_token))


(defn get-github-user-info
  [access-token]
  (parse-github-user
    (http/get "https://api.github.com/user"
              {:headers {"Authorization" (str "token " access-token)}})))
