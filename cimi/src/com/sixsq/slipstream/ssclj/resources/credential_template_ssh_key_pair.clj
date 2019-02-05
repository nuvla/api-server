(ns com.sixsq.slipstream.ssclj.resources.credential-template-ssh-key-pair
  "
Allows a Credential to be created that contains the SSH public key from a
generated SSH key pair. The generated private key is returned in the response
and not stored on, and cannot be recovered from the server.
"
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.credential-template :as p]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.spec.credential-template-ssh-key-pair :as ct-ssh-key-pair]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]))


(def ^:const credential-type "ssh-public-key")


(def ^:const resource-name "SSH Public/Private Keys")


(def ^:const resource-url credential-type)


(def ^:const method "generate-ssh-key-pair")


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
   :name        "Generate SSH Public Key"
   :description "public key of a generated SSH key pair"
   :size        1024
   :algorithm   "rsa"
   :acl         resource-acl})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-ssh-key-pair/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(defn initialize
  []
  (p/register resource)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ct-ssh-key-pair/schema)))
