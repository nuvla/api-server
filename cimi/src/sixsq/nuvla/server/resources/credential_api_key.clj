(ns sixsq.nuvla.server.resources.credential-api-key
  "
This represents an API Key and Secret pair that allows users to access the
SlipStream service independently of their account credentials. These
credentials can be time-limited.

It is often useful to have credentials to log into the SlipStream server that
are independent of your account credentials. This allows you, for example, to
provide time-limited access or to revoke access at any time without affecting
the access to your account with your main credentials.

For users who authenticate with external authentication mechanisms, an API key
and secret is mandatory for programmatic access to SlipStream, as the external
authentication mechanisms usually cannot be used with the API.

This example shows how to create an API key and secret credential.

An example document (named `create.json` below) for creating an API key and
secret with a lifetime of 1 day (86400 seconds).

```json
{
  \"credentialTemplate\" : {
                           \"href\" : \"credential-template/generate-api-key\",
                           \"ttl\" : 86400
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

When successful, the above command will return a 201 (created) status, a
'location' header with the created credential resource, and a JSON document
containing the plain text secret.

> NOTE: When the server generates a new API key and secret, the server returns
the plain text secret in the response. The server stores only a digest of the
secret, so you must capture and save the plain text secret from this response!
"
  (:require
    [sixsq.nuvla.auth.acl :as acl]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-api-key :as tpl]
    [sixsq.nuvla.server.resources.credential.key-utils :as key-utils]
    [sixsq.nuvla.server.resources.spec.credential-api-key :as api-key]))

(defn strip-session-role
  [roles]
  (vec (remove #(re-matches #"^session/.*" %) roles)))

(defn extract-claims [request]
  (let [{:keys [identity roles]} (acl/current-authentication request)
        roles (strip-session-role roles)]
    (cond-> {:identity identity}
            (seq roles) (assoc :roles (vec roles)))))

(def valid-ttl? (every-pred int? pos?))

;;
;; convert template to credential: loads and validates the given SSH public key
;; provides attributes about the key.
;;
(defmethod p/tpl->credential tpl/credential-type
  [{:keys [type method ttl]} request]
  (let [[secret-key digest] (key-utils/generate)
        resource (cond-> {:resource-type p/resource-uri
                          :type        type
                          :method      method
                          :digest      digest
                          :claims      (extract-claims request)}
                         (valid-ttl? ttl) (assoc :expiry (u/ttl->timestamp ttl)))]
    [{:secretKey secret-key} resource]))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::api-key/schema))
(defmethod p/validate-subtype tpl/credential-type
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::api-key/schema-create))
(defmethod p/create-validate-subtype tpl/credential-type
  [resource]
  (create-validate-fn resource))


;;
;; multimethod for edition
;;
(defmethod p/special-edit tpl/credential-type
  [resource request]
  (if ((:user-roles request) "ADMIN")
    resource
    (dissoc resource :claims)))

;;
;; initialization: no schema for this parent resource
;;
(defn initialize
  []
  (std-crud/initialize p/resource-type ::api-key/schema))
