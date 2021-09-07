(ns sixsq.nuvla.server.resources.credential-template-infrastructure-service-openstack
  "
Allows `docker-machine` credentials for Openstack to be created. The attribute
names correspond exactly to those required by `docker-machine`.
"
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-openstack :as service]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "infrastructure-service-openstack")


(def ^:const resource-url credential-subtype)


(def ^:const resource-name "Openstack API access")


(def ^:const method "store-infrastructure-service-openstack")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const resource
  {:subtype                 credential-subtype
   :method                  method
   :name                    resource-name
   :description             "Openstack cloud credentials"
   :openstack-username      ""
   :openstack-password      ""
   :openstack-tenant-id     ""
   :openstack-domain-name   ""
   :openstack-authz-url     ""
   :acl                     resource-acl
   :resource-metadata       "resource-metadata/credential-template-driver-openstack"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::service/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::service/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::service/schema-create "create"))


(defn initialize
  []
  (p/register resource)
  (md/register resource-metadata)
  (md/register resource-metadata-create))
