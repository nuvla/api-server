(ns com.sixsq.slipstream.auth.oidc
  (:require
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.logging :as log]))


(defn get-access-token
  "Perform an HTTP POST to the OIDC/MitreID server to recover an access token.
   This method will log exceptions but then return nil to indicate that the
   access token could not be retrieved."
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
        (json/read-str :key-fn keyword)
        :access_token)
    (catch Exception e
      (let [client-secret? (str (boolean client-secret))]
        (if-let [{:keys [status] :as data} (ex-data e)]
          (log/errorf "error status %s getting access token from %s with client_id %s, code %s, and client_secret %s\n%s"
                      status tokenURL client-id oidc-code client-secret? (with-out-str (pprint data)))
          (log/errorf "unexpected error when getting access token from %s with client_id %s, code %s, and client_secret %s\n%s"
                      tokenURL client-id oidc-code client-secret? (str e))))
      nil)))
