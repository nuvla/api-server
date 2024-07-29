(ns com.sixsq.nuvla.server.resources.session-oidc
  (:require
    [com.sixsq.nuvla.auth.utils.timestamp :as ts]
    [com.sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.hook :as hook]
    [com.sixsq.nuvla.server.resources.hook-oidc-session :as hook-oidc-session]
    [com.sixsq.nuvla.server.resources.session :as p]
    [com.sixsq.nuvla.server.resources.session-oidc.utils :as oidc-utils]
    [com.sixsq.nuvla.server.resources.session.utils :as sutils]
    [com.sixsq.nuvla.server.resources.spec.session :as session]
    [com.sixsq.nuvla.server.resources.spec.session-template-oidc :as st-oidc]))


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
  [{:keys [href instance redirect-url] :as _resource} {:keys [headers base-uri] :as _request}]
  (let [{:keys [client-id authorize-url]} (oidc-utils/config-oidc-params redirect-url instance)
        ;; fake session values, will be replaced after callback execution
        session      (-> (sutils/create-session
                           nil "user-id" {:href href} headers
                           authn-method redirect-url)
                         (assoc :expiry (ts/rfc822->iso8601
                                          (ts/expiry-later-rfc822
                                            login-request-timeout))))
        hook-url     (cond-> (str base-uri hook/resource-type "/" hook-oidc-session/action)
                             (not= instance oidc-utils/geant-instance) (str "/" instance))
        redirect-url (oidc-utils/create-redirect-url authorize-url client-id
                                                     hook-url "openid email")
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
