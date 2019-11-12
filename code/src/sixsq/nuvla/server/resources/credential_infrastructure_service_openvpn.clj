(ns sixsq.nuvla.server.resources.credential-infrastructure-service-openvpn
  "
This represents an OpenVPN client credential that allows users to access the
OpenVPN service.
"
  (:require
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-openvpn-customer
     :as tpl-customer]
    [sixsq.nuvla.server.resources.credential.openvpn-utils :as openvpn-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-openvpn :as ciso]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-openvpn :as ctiso]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::ctiso/schema))


(defn initialize
  []
  (std-crud/initialize p/resource-type ::ctiso/schema)
  (md/register resource-metadata))


;;
;; convert template to credential: just copies the necessary keys from the provided template.
;;

(defmethod p/tpl->credential tpl-customer/credential-subtype
  [{:keys [subtype method parent openvpn-csr]} request]
  (let [user-id         (auth/current-user-id request)
        authn-info      (auth/current-authentication request)
        customer?       (= method tpl-customer/method)
        expected-scope  (if customer? "customer" "nuvlabox")
        openvpn-service (openvpn-utils/get-service authn-info parent)]

    (openvpn-utils/check-service-subtype openvpn-service)
    (openvpn-utils/check-scope openvpn-service expected-scope)
    (openvpn-utils/check-existing-credential parent user-id)

    (let [configuration-openvpn (openvpn-utils/get-configuration parent)
          openvpn-endpoint      (:endpoint configuration-openvpn)]

      (openvpn-utils/check-openvpn-endpoint parent openvpn-endpoint)

      ;; call openvpn api
      (let [response-openvpn-api (openvpn-utils/generate-credential
                                   openvpn-endpoint user-id parent openvpn-csr)
            intermediate-ca      (:intermediate-ca response-openvpn-api)
            acl                  (if customer?
                                   {:owners   ["group/nuvla-admin"]
                                    :view-acl [user-id]
                                    :delete   [user-id]}
                                   {:owners   ["group/nuvla-admin"]
                                    :view-acl ["group/nuvla-nuvlabox"]
                                    :delete   ["group/nuvla-nuvlabox"]})]
        [response-openvpn-api
         (cond->
           {:resource-type       p/resource-type
            :subtype             subtype
            :method              method
            :openvpn-certificate (:certificate response-openvpn-api)
            :openvpn-common-name (:common-name response-openvpn-api)
            :openvpn-certificate-owner (auth/current-user-id request)
            :acl                 acl
            :parent              parent}
           intermediate-ca (assoc :openvpn-intermediate-ca intermediate-ca))]))))


(defmethod p/special-delete tpl-customer/credential-subtype
  [{is-id :parent cred-id :id} request]
  (let [configuration-openvpn (openvpn-utils/get-configuration is-id)
        openvpn-endpoint      (:endpoint configuration-openvpn)]

    (openvpn-utils/check-openvpn-endpoint is-id openvpn-endpoint)

    (openvpn-utils/delete-credential openvpn-endpoint cred-id))
  (p/delete-impl request))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ciso/schema))


(defmethod p/validate-subtype tpl-customer/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::ctiso/schema-create))


(defmethod p/create-validate-subtype tpl-customer/credential-subtype
  [resource]
  (create-validate-fn resource))
