(ns com.sixsq.nuvla.server.resources.user-github
  (:require
    [com.sixsq.nuvla.server.resources.callback :as callback]
    [com.sixsq.nuvla.server.resources.callback-create-user-github :as user-github-callback]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.github.utils :as gu]
    [com.sixsq.nuvla.server.resources.spec.user]
    [com.sixsq.nuvla.server.resources.spec.user-template-github :as ut-github]
    [com.sixsq.nuvla.server.resources.user-interface :as p]
    [com.sixsq.nuvla.server.resources.user-template-github :as user-template]))

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
  [{:keys [href instance redirect-url] :as _resource} {:keys [base-uri] :as _request}]
  (let [[client-id client-secret] (gu/config-github-params redirect-url instance)]
    (if (and client-id client-secret)
      (let [data         (when redirect-url {:redirect-url redirect-url})
            callback-url (create-user-github-callback base-uri href :data data)
            redirect-url (format gu/github-oath-endpoint client-id callback-url)]
        [{:status 303, :headers {"Location" redirect-url}} nil])
      (gu/throw-bad-client-config user-template/registration-method redirect-url))))
