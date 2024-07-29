(ns com.sixsq.nuvla.server.resources.credential-template-totp-2fa
  "
This resource allows nuvla server to store generated TOTP secret in `credential`
. A token allows server to authenticate a user.
"
  (:require
    [com.sixsq.nuvla.auth.utils.acl :as acl-utils]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.credential-template :as p]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.credential-template-totp-2fa :as totp]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "totp-2fa")


(def ^:const resource-url credential-subtype)


(def ^:const method credential-subtype)


(def resource-acl (acl-utils/normalize-acl {:owners ["group/nuvla-admin"]}))

;;
;; resource
;;

(def ^:const template
  {:subtype           credential-subtype
   :method            method
   :name              "Two factor authentication TOTP secret"
   :description       "stores a TOTP secret"
   :acl               resource-acl
   :resource-metadata "resource-metadata/credential-template-totp-2fa"})


;;
;; initialization: register this credential-template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::totp/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::totp/schema-create "create"))


(defn initialize
  []
  (p/register template)
  (md/register resource-metadata)
  (md/register resource-metadata-create))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::totp/schema))


(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


