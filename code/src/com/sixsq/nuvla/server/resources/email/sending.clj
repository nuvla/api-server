(ns com.sixsq.nuvla.server.resources.email.sending
  (:require
    [clj-http.client :as http]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.util.response :as r]
    [java-time :as t]
    [jsonista.core :as j]
    [postal.core :as postal]
    [selmer.parser :as tmpl]))

(def base-html (slurp (io/resource "sixsq/nuvla/html-template/base.html")))
(def trial-html (slurp (io/resource "sixsq/nuvla/html-template/trial.html")))
(def trial-txt (slurp (io/resource "sixsq/nuvla/txt-template/trial.txt")))

(def access-token-response! (atom nil))
(def xoauth2-config! (atom nil))
(def xoauth2-future! (atom nil))

(defn post-google-refresh-access-token
  []
  (let [{:keys [client-id client-secret refresh-token]} @xoauth2-config!]
    (try
      (let [response (-> (http/post "https://accounts.google.com/o/oauth2/token"
                                    {:headers     {"Accept" "application/json"}
                                     :form-params {:client_id     client-id
                                                   :client_secret client-secret
                                                   :refresh_token refresh-token
                                                   :grant_type    "refresh_token"}})
                         :body
                         j/read-value)]
        (log/info "Refresh SMTP google access token response: "
                  (update response "access_token" #(str (subs % 0 3) "[...]")))
        (reset! access-token-response! response))
      (catch Exception ex
        (reset! access-token-response! nil)
        (log/error "SMTP GOOGLE XOAUTH2 failed to refresh token!" (ex-data ex))))))

(defn compute-next-refresh-ms
  [access-token-response-value]
  (-> access-token-response-value
      (get "expires_in" 0)
      (* 1000)
      (- 30000)
      (max 5000)))

(defn sleep
  [ms]
  (^[long] Thread/sleep ms))

(defn schedule-refresh-token-before-expiry
  []
  (reset!
    xoauth2-future!
    (future
      (while true
        (let [next-refresh-ms (compute-next-refresh-ms @access-token-response!)]
          (log/info "SMTP xoauth2 will be automatically refreshed in:" next-refresh-ms)
          (sleep next-refresh-ms))
        (post-google-refresh-access-token)))))

(defn refresh-token-when-no-access-token-or-on-config-change!
  [smtp-xoauth2-config]
  (when (or (nil? @access-token-response!)
            (not= @xoauth2-config! smtp-xoauth2-config))
    (reset! xoauth2-config! smtp-xoauth2-config)
    (when @xoauth2-future! (future-cancel @xoauth2-config!))
    (post-google-refresh-access-token)
    (schedule-refresh-token-before-expiry)))

(defn get-google-access-token
  [{:keys [client-id client-secret refresh-token] :as smtp-xoauth2-config}]
  (if (and client-id client-secret refresh-token)
    (do
      (refresh-token-when-no-access-token-or-on-config-change! smtp-xoauth2-config)
      (get @access-token-response! "access_token"))
    (log/error "SMTP xoauth2 config must have 'client-id client-secret refresh-token' defined!")))

(defmulti extract-smtp-cfg :smtp-xoauth2)

(defmethod extract-smtp-cfg :default
  [{:keys [smtp-host smtp-port smtp-ssl
           smtp-username smtp-password smtp-xoauth2] :as nuvla-config}]
  (if (and nuvla-config (nil? smtp-xoauth2))
    {:host smtp-host
     :port smtp-port
     :ssl  smtp-ssl
     :user smtp-username
     :pass smtp-password}
    (when (some? smtp-xoauth2)
      (log/error "smtp xoauth2 not found!"))))

(defmethod extract-smtp-cfg "google"
  [nuvla-config]
  (when-let [{:keys [smtp-host smtp-port smtp-ssl
                     smtp-username smtp-xoauth2-config]} nuvla-config]
    {:host            smtp-host
     :port            smtp-port
     :ssl             smtp-ssl
     :user            smtp-username
     :pass            (get-google-access-token smtp-xoauth2-config)
     :auth.mechanisms "XOAUTH2"}))

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
