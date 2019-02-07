(ns sixsq.nuvla.server.resources.credential-ssh-public-key
  "
On a successful authentication, the above command will return a 201 (created)
status, a 'location' header with the created credential resource, and a JSON
document containing the SSH private key of the generated key pair.

The 'ssh-public-key' Credential resource stores the public key (in OpenSSH
format), the algorithm used to create the key ('rsa' or 'dsa'), and the
fingerprint of the key itself. You can create any number of SSH public key
credentials on the server.

These resources can be created either by providing the public key of an
existing SSH key pair or by having the server generate a new SSH key pair. In
the second case, the server will provide the private key in the 201 (created)
response.

> NOTE: When the server generates a new SSH key pair, the server returns the
private key in the response. The server does not store this private key, so you
must capture and save the key from this response!

An example document (named `create.json` below) for creating a new SSH key
pair.

```json
{
  \"credentialTemplate\" : {
                           \"href\" : \"credential-template/generate-ssh-key-pair\"
                         }
}
```

```shell
# Be sure to get the URL from the cloud entry point!
# The cookie options allow for automatic management of the
# SlipStream authentication token (cookie).
curl https://nuv.la/api/credential \\
     -X POST \\
     -H 'content-type: application/json' \\
     -d @create.json \\
     --cookie-jar ~/cookies -b ~/cookies -sS
```
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-ssh-public-key :as tpl]
    [sixsq.nuvla.server.resources.credential.ssh-utils :as ssh-utils]
    [sixsq.nuvla.server.resources.spec.credential-ssh-public-key :as ssh-public-key]
    [sixsq.nuvla.server.util.log :as logu]))

(defn import-key [common-info publicKey]
  [nil (merge (ssh-utils/load publicKey) common-info)])

(defn generate-key [common-info algorithm size]
  (let [ssh-key (merge (ssh-utils/generate algorithm size) common-info)]
    [(select-keys ssh-key #{:privateKey}) (dissoc ssh-key :privateKey)]))

;;
;; convert template to credential: loads and validates the given SSH public key
;; provides attributes about the key.
;;
(defmethod p/tpl->credential tpl/credential-type
  [{:keys [type method publicKey algorithm size]} request]
  (let [common-info {:resource-type p/resource-uri
                     :type        type
                     :method      method}]
    (try
      (if publicKey
        (import-key common-info publicKey)
        (generate-key common-info algorithm size))
      (catch Exception e
        (logu/log-and-throw-400 (str "error creating SSH public key credential: '" (.getMessage e) "'"))))))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ssh-public-key/schema))
(defmethod p/validate-subtype tpl/credential-type
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::ssh-public-key/schema-create))
(defmethod p/create-validate-subtype tpl/credential-type
  [resource]
  (create-validate-fn resource))


;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize p/resource-url ::ssh-public-key/schema))
