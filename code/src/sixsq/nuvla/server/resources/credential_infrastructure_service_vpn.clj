(ns sixsq.nuvla.server.resources.credential-infrastructure-service-vpn
  "
This represents an VPN client credential that allows users to access the
VPN service.
"
  (:require
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-vpn-customer
     :as tpl-customer]
    [sixsq.nuvla.server.resources.credential.vpn-utils :as vpn-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-vpn :as ciso]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-vpn :as ctiso]
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
  [{:keys [subtype method parent vpn-csr acl]} request]
  (let [user-id        (auth/current-user-id request)
        authn-info     (auth/current-authentication request)
        customer?      (= method tpl-customer/method)
        expected-scope (if customer? "customer" "nuvlabox")
        vpn-service    (vpn-utils/get-service authn-info parent)]

    (vpn-utils/check-service-subtype vpn-service)
    (vpn-utils/check-scope vpn-service expected-scope)
    (vpn-utils/check-existing-credential parent user-id)

    (let [configuration-vpn (vpn-utils/get-configuration parent)
          vpn-endpoint      (:endpoint configuration-vpn)]

      (vpn-utils/check-vpn-endpoint parent vpn-endpoint)

      ;; call vpn api
      (let [response-vpn-api (vpn-utils/try-generate-credential vpn-endpoint user-id parent vpn-csr)
            intermediate-ca  (:intermediate-ca response-vpn-api)]
        [response-vpn-api
         (cond->
           {:resource-type         p/resource-type
            :subtype               subtype
            :method                method
            :vpn-certificate       (:certificate response-vpn-api)
            :vpn-common-name       (:common-name response-vpn-api)
            :vpn-certificate-owner (auth/current-user-id request)
            :acl                   {:owners   ["group/nuvla-admin"]
                                    :view-acl [user-id, parent]
                                    :delete   [user-id]}
            :parent                parent}
           intermediate-ca (assoc :vpn-intermediate-ca intermediate-ca))]))))


(defmethod p/special-delete tpl-customer/credential-subtype
  [{is-id :parent cred-id :id} request]
  (let [configuration-vpn (vpn-utils/get-configuration is-id)
        vpn-endpoint      (:endpoint configuration-vpn)]

    (vpn-utils/check-vpn-endpoint is-id vpn-endpoint)

    (vpn-utils/try-delete-credential vpn-endpoint cred-id))

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
