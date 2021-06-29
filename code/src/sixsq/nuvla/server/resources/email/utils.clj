(ns sixsq.nuvla.server.resources.email.utils
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [postal.core :as postal]
    [selmer.parser :as tmpl]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-email-validation :as email-callback]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.util.response :as r])
  (:import (java.util Date)))

(def base-html (slurp (io/resource "sixsq/nuvla/html-template/base.html")))

(defn render-email
  [values-map]
  (tmpl/render
    base-html
    (assoc values-map :now (Date.))))

(def warning-initiate
  (str/join "\n"
            ["If you did not initiate this request, do NOT click on the link and report"
             "this to the service administrator."]))

(def conditions-acceptance
  (partial format
           (str/join "\n"
                     ["By clicking the link and validating your email address you accept the Terms"
                      "and Conditions:"
                      "\n    %s\n"])))

(defn validation-email-body
  [callback-url conditions-url]
  [:alternative
   {:type    "text/plain"
    :content (cond-> (format
                       (str/join "\n"
                                 ["To validate your email address, visit:"
                                  "\n    %s\n"
                                  warning-initiate])
                       callback-url)
                     conditions-url (str (conditions-acceptance conditions-url)))}
   {:type    "text/html; charset=utf-8"
    :content (render-email {:title            "Nuvla email validation"
                            :button-text      "Validate"
                            :button-url       callback-url
                            :text-1           "To validate your email address click the validate button."
                            :conditions-url   conditions-url
                            :warning-initiate true})}])


(defn invitation-email-body
  [name set-password-url conditions-url]
  [:alternative
   {:type    "text/plain"
    :content (cond-> (format (str/join "\n"
                                       ["You have been invited by \"%s\" to use Nuvla."
                                        " To accept the invitation, follow this link:"
                                        "\n    %s\n"]) name set-password-url)
                     conditions-url (str (conditions-acceptance conditions-url)))}
   {:type    "text/html; charset=utf-8"
    :content (render-email
               {:title          "Nuvla invitation"
                :button-text    "Accept invitation"
                :button-url     set-password-url
                :text-1         (str
                                  (format "You have been invited by \"%s\" to use Nuvla. " name)
                                  "To accept the invitation, click the following button:")
                :conditions-url conditions-url})}])


(defn create-callback [email-id base-uri]
  (let [callback-request {:params      {:resource-name callback/resource-type}
                          :body        {:action          email-callback/action-name
                                        :target-resource {:href email-id}}
                          :nuvla/authn auth/internal-identity}

        {{:keys [resource-id]} :body status :status} (crud/add callback-request)]
    (if (= 201 status)
      (if-let [callback-resource (crud/set-operations
                                   (crud/retrieve-by-id-as-admin resource-id) {})]
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
      (when-not (= :SUCCESS (:error resp))
        (let [msg (str "cannot send verification email: " (:message resp))]
          (throw (r/ex-bad-request msg)))))
    (catch Exception _
      (let [error-msg "server configuration for SMTP is missing"]
        (throw (ex-info error-msg (r/map-response error-msg 500)))))))


(defn send-validation-email [callback-url address]
  (let [{:keys [smtp-username conditions-url]
         :as   nuvla-config} (crud/retrieve-by-id-as-admin config-nuvla/config-instance-url)

        body (validation-email-body callback-url conditions-url)

        msg  {:from    (or smtp-username "administrator")
              :to      [address]
              :subject "Validation email for Nuvla service"
              :body    body}]

    (send-email nuvla-config msg)))


(defn send-invitation-email [set-password-url address {:keys [name id] :as _user}]
  (let [{:keys [smtp-username conditions-url]
         :as   nuvla-config} (crud/retrieve-by-id-as-admin config-nuvla/config-instance-url)

        body (invitation-email-body (or name id) set-password-url conditions-url)

        msg  {:from    (or smtp-username "administrator")
              :to      [address]
              :subject "Invitation by email for Nuvla service"
              :body    body}]

    (send-email nuvla-config msg)))


(defn password-set-email-body
  [set-password-url]
  [:alternative
   {:type    "text/plain"
    :content (format
               (str/join "\n"
                         ["To set your password visit:"
                          "\n    %s\n"
                          warning-initiate])
               set-password-url)}
   {:type    "text/html; charset=utf-8"
    :content (render-email
               {:title            "Nuvla set password"
                :button-text      "Set new password"
                :button-url       set-password-url
                :text-1           "To set your password click following button:"
                :warning-initiate true})}])


(defn join-group-email-body
  [group invited-by callback-url conditions-url]
  (let [msg (format "You have been invited by \"%s\" to join \"%s\" on Nuvla. " invited-by group)]
    [:alternative
     {:type    "text/plain"
      :content (cond-> (format
                         (str/join "\n"
                                   [msg
                                    "To accept the invitation, visit:"
                                    "\n    %s\n"])
                         callback-url)
                       conditions-url (str (conditions-acceptance conditions-url)))}
     {:type    "text/html; charset=utf-8"
      :content (render-email
                 {:title          (format "You’re invited to join %s" group)
                  :button-text    "Accept invitation"
                  :button-url     callback-url
                  :text-1         (str
                                    msg
                                    "To accept the invitation, click the following button:")
                  :conditions-url conditions-url})}]))


(defn send-password-set-email [set-password-url address]
  (let [{:keys [smtp-username] :as nuvla-config} (crud/retrieve-by-id-as-admin
                                                   config-nuvla/config-instance-url)

        body (password-set-email-body set-password-url)

        msg  {:from    (or smtp-username "administrator")
              :to      [address]
              :subject "Set password for Nuvla service"
              :body    body}]

    (send-email nuvla-config msg)))


(defn send-join-group-email [group invited-by callback-url address]
  (let [{:keys [smtp-username conditions-url] :as nuvla-config} (crud/retrieve-by-id-as-admin
                                                                  config-nuvla/config-instance-url)

        body (join-group-email-body group invited-by callback-url conditions-url)

        msg  {:from    (or smtp-username "administrator")
              :to      [address]
              :subject (format "You’re invited to join %s" group)
              :body    body}]

    (send-email nuvla-config msg)))
