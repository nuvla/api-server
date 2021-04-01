(ns sixsq.nuvla.server.resources.session-oidc
  (:require
    [clojure.string :as str]
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
    [sixsq.nuvla.server.resources.spec.session-template-oidc :as st-oidc]))


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

(defn get-authorize-url
  [{:keys [href instance redirect-url] :as resource} {:keys [headers base-uri] :as request}]
  (let [{:keys [client-id authorize-url
                redirect-url-resource]} (oidc-utils/config-oidc-params redirect-url instance)
        redirect-url (if (= redirect-url-resource "callback")
                       (let [session      (-> (sutils/create-session
                                                nil "user-id" {:href href} headers
                                                authn-method redirect-url)
                                              (assoc :expiry (ts/rfc822->iso8601
                                                               (ts/expiry-later-rfc822
                                                                 login-request-timeout))))]
                         ;; fake session values, will be replaced after callback execution
                         (sutils/create-callback base-uri (:id session) cb/action-name))
                       (str base-uri hook/resource-type "/" hook-oidc-session/action))]
    (oidc-utils/create-redirect-url authorize-url client-id redirect-url)))


(defmethod p/tpl->session authn-method
  [resource request]
  [{:status 303, :headers {"Location" (get-authorize-url resource request)}} nil])


;;
;; initialization: no schema for this parent resource
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::session/session))
