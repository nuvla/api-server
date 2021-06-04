(ns sixsq.nuvla.server.resources.credential-template-gpg-key
  "
Used to create a GPG keypair. The private key is only stored in the server if the
user passes it along with the GPG key creation request, otherwise only the public key is kept.
"
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-gpg-key :as ct-gpg-key]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "gpg-key")


(def ^:const resource-name "GPG Keypair")


(def ^:const resource-url credential-subtype)


(def ^:const method "gpg-key")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const resource
  {:subtype           credential-subtype
   :method            method
   :name              "Create GPG Keypair"
   :description       "Creates a GPG key"
   :acl               resource-acl
   :resource-metadata "resource-metadata/credential-template-gpg-key"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-gpg-key/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::ct-gpg-key/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::ct-gpg-key/schema-create "create"))


(defn initialize
  []
  (p/register resource)
  (md/register resource-metadata)
  (md/register resource-metadata-create))


