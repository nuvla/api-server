(ns sixsq.nuvla.server.resources.email.utils
  (:require
    [clojure.string :as str]
    [postal.core :as postal]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-email-validation :as email-callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.util.response :as r]))


(def validation-email-body
  (partial format
           (str/join "\n"
                     ["To validate your email address, visit:"
                      "\n    %s\n"
                      "If you did not initiate this request, do NOT click on the link and report"
                      "this to the service administrator."])))


(def conditions-acceptance
  (partial format
           (str/join "\n"
                     ["By clicking the link and validating your email address you accept the Terms"
                      "and Conditions:"
                      "\n    %s\n"])))


(def invitation-email-body
  (partial format
           (str/join "\n"
                     ["You have been invited by \"%s\" to use Nuvla."
                      "Signup with this email \"%s\" to accept this invite by following this link:"
                      "\n    %s\n"])))


(defn create-callback [email-id base-uri]
  (let [callback-request {:params      {:resource-name callback/resource-type}
                          :body        {:action          email-callback/action-name
                                        :target-resource {:href email-id}}
                          :nuvla/authn auth/internal-identity}

        {{:keys [resource-id]} :body status :status} (crud/add callback-request)]
    (if (= 201 status)
      (if-let [callback-resource (crud/set-operations (crud/retrieve-by-id-as-admin resource-id) {})]
        (if-let [validate-op (u/get-op callback-resource "execute")]
          (str base-uri validate-op)
          (let [msg "callback does not have execute operation"]
            (throw (ex-info msg (r/map-response msg 500 resource-id)))))
        (let [msg "cannot retrieve email validation callback"]
          (throw (ex-info msg (r/map-response msg 500 resource-id)))))
      (let [msg "cannot create email validation callback"]
        (throw (ex-info msg (r/map-response msg 500 email-id)))))))


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


(defn send-email [nuvla-config email-data]
  (try
    (let [smtp-config (extract-smtp-cfg nuvla-config)
          resp        (postal/send-message smtp-config email-data)]
      (if-not (= :SUCCESS (:error resp))
        (let [msg (str "cannot send verification email: " (:message resp))]
          (throw (r/ex-bad-request msg)))))
    (catch Exception e
      (let [error-msg "server configuration for SMTP is missing"]
        (throw (ex-info error-msg (r/map-response error-msg 500)))))))


(defn send-validation-email [callback-url address]
  (let [{:keys [smtp-username conditions-url]
         :as   nuvla-config} (crud/retrieve-by-id-as-admin config-nuvla/config-instance-url)

        body (cond-> (validation-email-body callback-url)
                     conditions-url (str (conditions-acceptance conditions-url)))

        msg  {:from    (or smtp-username "administrator")
              :to      [address]
              :subject "email validation"
              :body    body}]

    (send-email nuvla-config msg)))


(defn send-invitation-email [callback-url address {:keys [name id] :as user}]
  (let [{:keys [smtp-username, conditions-url]
         :as   nuvla-config} (crud/retrieve-by-id-as-admin config-nuvla/config-instance-url)

        body (cond-> (invitation-email-body (or name id) address callback-url)
                     conditions-url (str (conditions-acceptance conditions-url)))

        msg  {:from    (or smtp-username "administrator")
              :to      [address]
              :subject "email invitation"
              :body    body}]

    (send-email nuvla-config msg)))


(def password-reset-email-body
  (partial format
           (str/join "\n"
                     ["To reset your password visit:"
                      "\n    %s\n"
                      "If you did not initiate this request, do NOT click on the link and report"
                      "this to the service administrator."])))


(defn send-password-reset-email [callback-url address]
  (let [{:keys [smtp-username] :as nuvla-config} (crud/retrieve-by-id-as-admin config-nuvla/config-instance-url)

        body (password-reset-email-body callback-url)

        msg  {:from    (or smtp-username "administrator")
              :to      [address]
              :subject "reset password"
              :body    body}]

    (send-email nuvla-config msg)))
