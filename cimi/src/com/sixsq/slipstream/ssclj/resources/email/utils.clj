(ns com.sixsq.slipstream.ssclj.resources.email.utils
  (:require
    [clojure.string :as str]
    [com.sixsq.slipstream.ssclj.resources.callback :as callback]
    [com.sixsq.slipstream.ssclj.resources.callback-email-validation :as email-callback]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.util.response :as r]
    [postal.core :as postal]))


(def validation-email-body
  (partial format
           (str/join "\n"
                     ["To validate your email address, visit:"
                      "\n    %s\n"
                      "If you did not initiate this request, do NOT click on the link and report"
                      "this to the service administrator."])))


(def t-and-c-acceptance
  (partial format
           (str/join "\n"
                     ["By clicking the link and validating your email address you accept the Terms"
                      "and Conditions:"
                      "\n    %s\n"])))


;; FIXME: Fix ugliness around needing to create ring requests with authentication!
(defn create-callback [email-id baseURI]
  (let [callback-request {:params   {:resource-name callback/resource-url}
                          :body     {:action         email-callback/action-name
                                     :targetResource {:href email-id}}
                          :identity {:current         "INTERNAL"
                                     :authentications {"INTERNAL" {:identity "INTERNAL"
                                                                   :roles    ["ADMIN"]}}}}
        {{:keys [resource-id]} :body status :status} (crud/add callback-request)]
    (if (= 201 status)
      (if-let [callback-resource (crud/set-operations (crud/retrieve-by-id-as-admin resource-id) {})]
        (if-let [validate-op (u/get-op callback-resource "execute")]
          (str baseURI validate-op)
          (let [msg "callback does not have execute operation"]
            (throw (ex-info msg (r/map-response msg 500 resource-id)))))
        (let [msg "cannot retrieve email validation callback"]
          (throw (ex-info msg (r/map-response msg 500 resource-id)))))
      (let [msg "cannot create email validation callback"]
        (throw (ex-info msg (r/map-response msg 500 email-id)))))))


(defn smtp-cfg
  "Extracts the SMTP configuration from the server's configuration resource.
   Note that this assumes a standard URL for the configuration resource."
  []
  (when-let [{:keys [mailHost mailPort
                     mailSSL
                     mailUsername mailPassword
                     termsAndConditions]} (crud/retrieve-by-id-as-admin "configuration/slipstream")]
    {:host        mailHost
     :port        mailPort
     :ssl         mailSSL
     :user        mailUsername
     :pass        mailPassword
     :t-and-c-url termsAndConditions}))


(defn send-validation-email [callback-url address]
  (try
    (let [{:keys [user t-and-c-url] :as smtp} (smtp-cfg)]
      (let [sender (or user "administrator")
            body (cond-> (validation-email-body callback-url)
                         t-and-c-url (str (t-and-c-acceptance t-and-c-url)))
            msg {:from    sender
                 :to      [address]
                 :subject "email validation"
                 :body    body}
            resp (postal/send-message smtp msg)]
        (if-not (= :SUCCESS (:error resp))
          (let [msg (str "cannot send verification email: " (:message resp))]
            (throw (r/ex-bad-request msg))))))
    (catch Exception e
      (let [error-msg "server configuration for SMTP is missing"]
        (throw (ex-info error-msg (r/map-response error-msg 500)))))))
