(ns com.sixsq.slipstream.ssclj.resources.user-github
  (:require
    [com.sixsq.slipstream.ssclj.resources.callback :as callback]
    [com.sixsq.slipstream.ssclj.resources.callback-create-user-github :as user-github-callback]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.github.utils :as gu]
    [com.sixsq.slipstream.ssclj.resources.spec.user]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template-github :as ut-github]
    [com.sixsq.slipstream.ssclj.resources.user :as p]
    [com.sixsq.slipstream.ssclj.resources.user-template-github :as user-template]))

;;
;; multimethods for validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::ut-github/schema-create))
(defmethod p/create-validate-subtype user-template/registration-method
  [create-document]
  (create-validate-fn create-document))


;;
;; transform template into user resource
;; strips method attribute and updates the resource URI
;;

(def create-user-github-callback (partial callback/create user-github-callback/action-name))



(defmethod p/tpl->user user-template/registration-method
  [{:keys [href instance redirectURI] :as resource} {:keys [base-uri] :as request}]
  (let [[client-id clientSecret] (gu/config-github-params redirectURI instance)]
    (if (and client-id clientSecret)
      (let [data (when redirectURI {:redirectURI redirectURI})
            callback-url (create-user-github-callback base-uri href data)
            redirect-url (format gu/github-oath-endpoint client-id callback-url)]
        [{:status 303, :headers {"Location" redirect-url}} nil])
      (gu/throw-bad-client-config user-template/registration-method redirectURI))))
