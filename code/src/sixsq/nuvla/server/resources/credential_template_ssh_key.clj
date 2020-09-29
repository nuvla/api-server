(ns sixsq.nuvla.server.resources.credential-template-ssh-key
  "
Used to create an SSH keypair. The private key is only stored in the server if the
user passes it along with the SSH key creation request, otherwise only the public key is kept.
"
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-ssh-key :as ct-ssh-key]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "ssh-key")


(def ^:const resource-name "SSH Keypair")


(def ^:const resource-url credential-subtype)


(def ^:const method "generate-ssh-key")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const resource
  {:subtype           credential-subtype
   :method            method
   :name              "Generate SSH Keypair"
   :description       "generates an SSH key"
   :acl               resource-acl
   :resource-metadata "resource-metadata/credential-template-ssh-key"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-ssh-key/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::ct-ssh-key/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::ct-ssh-key/schema-create "create"))


(defn initialize
  []
  (p/register resource)
  (md/register resource-metadata)
  (md/register resource-metadata-create))


