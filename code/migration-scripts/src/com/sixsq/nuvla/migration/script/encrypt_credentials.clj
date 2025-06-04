(ns com.sixsq.nuvla.migration.script.encrypt-credentials
  (:require [clojure.string :as str]
            [com.sixsq.nuvla.migration.api-client :as api]))

(defonce creds (atom nil))

(defn fetch-all-creds-with-secrets
  []
  (reset! creds
          (->> (api/credentials
                 {:filter (str/join " or " (map #(str (name %) "!=null") [:secret :password :vpn-certificate :key :private-key :token :secret-key]))
                  :last   10000
                  :orderby "id:asc"})
               :resources)))


(defn edit-secrets-to-force-encrypt
  [creds]
  (doseq [{:keys [id] :as cred} creds]
    (try
      (api/patch-resource id cred)
      (catch Exception ex
        (prn "Failed to update credential " id ": " ex)))))

(comment
  (api/reset-session)
  (api/ensure-admin-session
    :dev-kb
    ;:preprod
    ;:prod
    )
  (fetch-all-creds-with-secrets)
  (count @creds)
  (edit-secrets-to-force-encrypt @creds))
