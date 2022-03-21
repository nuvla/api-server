(ns sixsq.nuvla.server.resources.email.sending
  (:require
    [clojure.java.io :as io]
    [selmer.parser :as tmpl]
    [postal.core :as postal]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [clojure.tools.logging :as log])
  (:import
    (java.util Date)))

(def base-html (slurp (io/resource "sixsq/nuvla/html-template/base.html")))
(def trial-html (slurp (io/resource "sixsq/nuvla/html-template/trial.html")))
(def trial-txt (slurp (io/resource "sixsq/nuvla/txt-template/trial.txt")))

(defn extract-smtp-cfg
  "Extracts the SMTP configuration from the server's configuration resource.
   Note that this assumes a standard URL for the configuration resource."
  [nuvla-config]
  (when-let [{:keys [smtp-host smtp-port
                     smtp-ssl
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
      (when-not (= :SUCCESS (:error resp))
        (let [msg (str "cannot send verification email: " (:message resp))]
          (log/error "Dispatch email failed: " resp)
          (throw (r/ex-bad-request msg)))))
    (catch Exception _
      (let [error-msg "server configuration for SMTP is missing"]
        (throw (ex-info error-msg (r/map-response error-msg 500)))))))

(defn render-content
  [{:keys [template plain?] :as context-map}]
  (tmpl/render
    (case template
      :trial (if plain? trial-txt trial-html)
      base-html)
    (assoc context-map :now (Date.))))

(defn send-email
  "send email to an email-address using a map from resources.email.text .e.g. email.text/trial-ending"
  [to email-data]
  (let [{:keys [smtp-username] :as nuvla-config} (crud/retrieve-by-id-as-admin config-nuvla/config-instance-url)]
    (dispatch nuvla-config (assoc email-data
                             :subject (:subject email-data)
                             :body [:alternative
                                    {:type    "text/plain"
                                     :content (render-content (assoc email-data :plain? true))}
                                    {:type    "text/html; charset=utf-8"
                                     :content (render-content email-data)}]
                             :from (or smtp-username "administrator")
                             :to [to]))))
