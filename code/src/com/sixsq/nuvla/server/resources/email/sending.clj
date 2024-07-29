(ns com.sixsq.nuvla.server.resources.email.sending
  (:require
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [java-time :as t]
    [postal.core :as postal]
    [selmer.parser :as tmpl]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.util.response :as r]))

(def base-html (slurp (io/resource "sixsq/nuvla/html-template/base.html")))
(def trial-html (slurp (io/resource "sixsq/nuvla/html-template/trial.html")))
(def trial-txt (slurp (io/resource "sixsq/nuvla/txt-template/trial.txt")))

(defn extract-smtp-cfg
  "Extracts the SMTP configuration from the server's configuration resource.
   Note that this assumes a standard URL for the configuration resource."
  [nuvla-config]
  (when-let [{:keys [smtp-host smtp-port smtp-ssl
                     smtp-username smtp-password]} nuvla-config]
    {:host smtp-host
     :port smtp-port
     :ssl  smtp-ssl
     :user smtp-username
     :pass smtp-password}))

(defn dispatch
  [nuvla-config email-data]
  (try
    (let [smtp-config (extract-smtp-cfg nuvla-config)
          resp        (postal/send-message smtp-config email-data)]
      (if (= :SUCCESS (:error resp))
        {:success? true}
        (throw (ex-info "sending email failed:" {:causes resp}))))
    (catch Exception ex
      (let [corr-id   (rand-int 999999)
            error-msg (str "email dispatch failed! Correlation ID: " corr-id)]
        (log/error error-msg
                   "Message:" (ex-message ex)
                   "Data:" (ex-data ex)
                   "Cause:" (ex-cause ex))
        (throw (ex-info error-msg (r/map-response error-msg 500)))))))

(defn render-content
  [{:keys [template plain?] :as context-map}]
  (tmpl/render
    (case template
      :trial (if plain? trial-txt trial-html)
      base-html)
    (assoc context-map :now (t/java-date))))

(defn send-email
  "send email to an email-address using a map from resources.email.text
  e.g. email.text/trial-ending"
  [to email-data]
  (let [{:keys [smtp-username email-header-img-url]
         :as   nuvla-config} (crud/retrieve-by-id-as-admin
                               config-nuvla/config-instance-url)]
    (dispatch nuvla-config
              {:subject (:subject email-data)
               :body    [:alternative
                         {:type    "text/plain"
                          :content (render-content
                                     (assoc email-data :plain? true))}
                         {:type    "text/html; charset=utf-8"
                          :content (render-content
                                     (assoc email-data
                                       :header-img email-header-img-url))}]
               :from    (or smtp-username "administrator")
               :to      [to]})))
