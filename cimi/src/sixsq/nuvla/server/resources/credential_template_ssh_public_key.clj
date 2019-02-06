(ns sixsq.nuvla.server.resources.credential-template-ssh-public-key
  "
Allows a Credential to be created that contains the SSH public key from a SSH
key pair generated elsewhere. The SSH public key can be in either RSA or DSA
format.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-ssh-public-key :as ct-ssh-public-key]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-type "ssh-public-key")


(def ^:const resource-name "SSH Public Key")


(def ^:const resource-url credential-type)


(def ^:const method "import-ssh-public-key")


(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "USER"
                            :type      "ROLE"
                            :right     "VIEW"}]})

;;
;; resource
;;

(def ^:const resource
  {:type        credential-type
   :method      method
   :name        "Import SSH Public Key"
   :description "import public key of an existing SSH key pair"
   :publicKey   "ssh-public-key"
   :acl         resource-acl})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-ssh-public-key/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(defn initialize
  []
  (p/register resource)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ct-ssh-public-key/schema)))
