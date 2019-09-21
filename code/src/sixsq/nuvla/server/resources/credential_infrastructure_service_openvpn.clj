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
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-openvpn-customer :as tpl-customer]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
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
  [{:keys [subtype method parent openvpn-certificate openvpn-common-name]} request]
  (let [user-id (auth/current-user-id request)
        acl     (if (= method tpl-customer/method)
                  {:owners   ["group/nuvla-admin"]
                   :view-acl [user-id]
                   :delete   [user-id]}
                  {:owners   ["group/nuvla-admin"]
                   :view-acl ["group/nuvla-nuvlabox"]
                   :delete   ["group/nuvla-nuvlabox"]})]
    [nil (cond-> {:resource-type       p/resource-type
                  :subtype             subtype
                  :method              method
                  :openvpn-certificate openvpn-certificate
                  :openvpn-common-name openvpn-common-name
                  :acl                 acl}
                 parent (assoc :parent parent))]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ctiso/schema))


(defmethod p/validate-subtype tpl-customer/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::ctiso/schema-create))


(defmethod p/create-validate-subtype tpl-customer/credential-subtype
  [resource]
  (create-validate-fn resource))
