(ns sixsq.nuvla.auth.oidc
  (:require
    [buddy.sign.jws :as jws]
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.logging :as log]))

(defn get-token
  "Perform an HTTP POST to the OIDC/MitreID server to recover a token.
   This method will log exceptions but then return nil to indicate that the
   token could not be retrieved."
  [client-id client-secret tokenURL oidc-code redirect-uri]
  (try
    (-> (http/post tokenURL
                   {:headers     {"Accept" "application/json"}
                    :form-params {:grant_type    "authorization_code"
                                  :code          oidc-code
                                  :redirect_uri  redirect-uri
                                  :client_id     client-id
                                  :client_secret client-secret}})
        :body
        (json/read-str :key-fn keyword))
    (catch Exception e
      (let [client-secret? (str (boolean client-secret))]
        (if-let [{:keys [status] :as data} (ex-data e)]
          (log/errorf "error status %s getting access token from %s with client_id %s, code %s, and client_secret %s\n%s"
                      status tokenURL client-id oidc-code client-secret? (with-out-str (pprint data)))
          (log/errorf "unexpected error when getting access token from %s with client_id %s, code %s, and client_secret %s\n%s"
                      tokenURL client-id oidc-code client-secret? (str e)))))))

(defn get-access-token
  [client-id client-secret tokenURL oidc-code redirect-uri]
  (:access_token
    (get-token client-id client-secret tokenURL oidc-code redirect-uri)))

(defn get-id-token
  [client-id client-secret tokenURL oidc-code redirect-uri]
  (:id_token
    (get-token client-id client-secret tokenURL oidc-code redirect-uri)))

(defn get-kid-from-id-token
  [id-token]
  (try
    (-> id-token jws/decode-header :kid)
    (catch Exception e
      (log/errorf "OIDC extraction of kid from id-token failed: %s (%s)"
                  id-token (str e)))))

(defn get-public-key
  [jwks-url kid]
  (log/debugf "getting public key from jwks-url='%s' with kid='%s'" jwks-url kid)
  (try
    (-> (http/get jwks-url {:headers {"Accept" "application/json"}})
        :body
        (json/read-str :key-fn keyword)
        :keys
        (->> (some #(when (= (:kid %) kid) (json/write-str %)))))
    (catch Exception e
      (if-let [{:keys [status] :as data} (ex-data e)]
        (log/errorf
          "OIDC jwks public key failed with status %s from %s \n%s"
          status jwks-url (with-out-str (pprint data)))
        (log/errorf
          "OIDC unexpected error when getting public key from %s \n%s"
          jwks-url (str e))))))
