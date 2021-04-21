(ns sixsq.nuvla.server.resources.session-oidc
  (:require
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.server.resources.callback-create-session-oidc :as cb]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.hook :as hook]
    [sixsq.nuvla.server.resources.hook-oidc-session :as hook-oidc-session]
    [sixsq.nuvla.server.resources.session :as p]
    [sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [sixsq.nuvla.server.resources.session.utils :as sutils]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.resources.spec.session-template-oidc :as st-oidc]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.server.middleware.authn-info :as authn-info]))


(def ^:const authn-method "oidc")


(def ^:const login-request-timeout (* 3 60))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::session/session))


(defmethod p/validate-subtype authn-method
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::st-oidc/schema-create))


(defmethod p/create-validate-subtype authn-method
  [resource]
  (create-validate-fn resource))


;;
;; transform template into session resource
;;

(defmethod p/tpl->session authn-method
  [{:keys [href instance redirect-url] :as resource} {:keys [headers base-uri] :as request}]
  (let [{:keys [client-id authorize-url
                redirect-url-resource]} (oidc-utils/config-oidc-params redirect-url instance)
        ;; fake session values, will be replaced after callback execution
        session      (-> (sutils/create-session
                           nil "user-id" {:href href} headers
                           authn-method redirect-url)
                         (assoc :expiry (ts/rfc822->iso8601
                                          (ts/expiry-later-rfc822
                                            login-request-timeout))))
        callback-url (if (= redirect-url-resource "callback")
                       (sutils/create-callback base-uri (:id session) cb/action-name)
                       (str base-uri hook/resource-type "/" hook-oidc-session/action))
        redirect-url (oidc-utils/create-redirect-url authorize-url client-id
                                                     callback-url "openid email" (:id session))

        cookie-info (cookies/create-cookie-info "group/anon"
                                                :session-id (:id session)
                                                :headers headers
                                                :client-ip (:client-ip session))
        cookie       {:value   (:id session)
                      :expires (ts/rfc822 (ts/expiry-later))}]
    [{:status  303
      :cookies {authn-info/future-session-cookie cookie}
      :headers {"Location" redirect-url}} session]))


;;
;; initialization: no schema for this parent resource
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::session/session))
