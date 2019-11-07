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
    [sixsq.nuvla.server.util.log :as logu]
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
  [{:keys [subtype method parent]}
   request]
  (let [user-id         (auth/current-user-id request)
        authn-info      (auth/current-authentication request)
        customer?       (= method tpl-customer/method)
        openvpn-service (openvpn-utils/get-service authn-info parent)

        acl             (if customer?
                          {:owners   ["group/nuvla-admin"]
                           :view-acl [user-id]
                           :delete   [user-id]}
                          {:owners   ["group/nuvla-admin"]
                           :view-acl ["group/nuvla-nuvlabox"]
                           :delete   ["group/nuvla-nuvlabox"]})]

    (when (not= (:subtype openvpn-service) "openvpn")
      (logu/log-and-throw-400
        "Bad infrastructure service subtype. Subtype should be openvpn!"))

    (when (not= (:openvpn-scope openvpn-service)
                (if customer? "customer" "nuvlabox"))
      (logu/log-and-throw-400
        "Bad infrastructure service scope for selected credential template!"))

    (when (openvpn-utils/credentials-already-exist? parent user-id)
      (logu/log-and-throw-400
        "Credential with following common-name already exist!"))


    (let [{openvpn-endpoint :endpoint} (openvpn-utils/get-configuration parent)]
      (when-not openvpn-endpoint
        (logu/log-and-throw-400
          (format "No openvpn api endpoint found for '%s'." parent)))

      ;; call openvpn api
      (let [response-openvpn-api (openvpn-utils/generate-credential
                                   openvpn-endpoint user-id parent)
            intermediate-ca      (:intermediate-ca response-openvpn-api)]
        [nil (cond->
               {:resource-type       p/resource-type
                :subtype             subtype
                :method              method
                :openvpn-certificate (:certificate response-openvpn-api)
                :openvpn-common-name (:common-name response-openvpn-api)
                :acl                 acl
                :parent              parent}
               intermediate-ca (assoc :openvpn-intermediate-ca intermediate-ca))]))))

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
