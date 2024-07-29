(ns com.sixsq.nuvla.server.resources.credential-ssh-key
  "
This represents an SSH key. It is a resource for Nuvla users to
generate and store Nuvla-related SSH credentials.

In some cases, this credential is also used by Nuvla to issue actions
via SSH, towards an existing Infrastructure, like a NuvlaBox.

```json
{
  \"template\" : {
                   \"href\" : \"credential-template/generate-ssh-key\",
                   \"public-key\" : \"ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC9o78r4jxXo6ZKB...\",
                   \"private-key\": \"-----BEGIN RSA PRIVATE KEY-----\n\"
                 }
}
```

When successful, the above command will return a 201 (created) status, a
'location' header with the created credential resource, and a JSON document
containing the plain text secret.

> NOTE: When the server generates a new SSH key, the server returns
the plain text private key in the response. **You must save the plain text private key
from the response! The secret cannot be recovered from the server later.**
"
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.credential :as p]
    [com.sixsq.nuvla.server.resources.credential-template-ssh-key :as tpl]
    [com.sixsq.nuvla.server.resources.credential.key-utils :as key-utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.credential-ssh-key :as ssh-key]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


;;
;; convert template to credential: loads and validates the given SSH public key
;; provides attributes about the key.
;;
(defmethod p/tpl->credential tpl/credential-subtype
  [{:keys [subtype method public-key private-key acl]} _request]
  (let [[pubkey pvtkey] (if public-key [public-key private-key] (key-utils/generate-ssh-keypair))
        resource (cond-> {:resource-type p/resource-type
                          :subtype       subtype
                          :method        method
                          :acl           acl
                          :public-key    pubkey}
                        (and (string? private-key) (string? public-key)) (assoc :private-key private-key))
        return  (cond-> {:public-key pubkey}
                  (string? pvtkey) (assoc :private-key pvtkey))]
    [return resource]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ssh-key/schema))
(defmethod p/validate-subtype tpl/credential-subtype
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::ssh-key/schema-create))
(defmethod p/create-validate-subtype tpl/credential-subtype
  [resource]
  (create-validate-fn resource))


;;;
;;; multimethod for edition
;;;
;(defmethod p/special-edit tpl/credential-subtype
;  [resource {:keys [nuvla/authn] :as request}]
;  (if (acl-resource/is-admin? authn)
;    resource
;    (dissoc resource :claims)))

;;
;; initialization: no schema for this parent resource
;;


(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::ssh-key/schema))


(defn initialize
  []
  (md/register resource-metadata))
