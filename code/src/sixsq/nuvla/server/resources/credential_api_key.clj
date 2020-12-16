(ns sixsq.nuvla.server.resources.credential-api-key
  "
This represents an API key and secret pair that allows users to access the
Nuvla service independently of their user credentials. These credentials can be
time-limited and can be revoked at any time.

It is often useful to have credentials to log into the Nuvla server that are
independent of your account credentials. This allows you, for example, to
provide access to Nuvla resources to automated agents without having to share
the main credentials for your account.

For users who authenticate with external authentication mechanisms, an API key
and secret is mandatory for programmatic access to Nuvla, as the external
authentication mechanisms usually require interactions via a web browser and
cannot be used with the API.

This example shows how to create an API key and secret credential.

An example document (named `create.json` below) for creating an API key and
secret with a lifetime of 1 day (86400 seconds).

```json
{
  \"template\" : {
                   \"href\" : \"credential-template/generate-api-key\",
                   \"ttl\" : 86400
                 }
}
```

```shell
# Be sure to get the URL from the cloud entry point!
# The cookie options allow for automatic management of the
# Nuvla authentication token (cookie).
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
the plain text secret in the response. **You must save the plain text secret
from the response! The secret cannot be recovered from the server later.**
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as acl-resource]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-api-key :as tpl]
    [sixsq.nuvla.server.resources.credential.key-utils :as key-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-api-key :as api-key]
    [sixsq.nuvla.server.util.metadata :as gen-md]))

(defn strip-session-role
  [roles]
  (vec (remove #(re-matches #"^session/.*" %) roles)))

(defn extract-claims [request]
  (let [{:keys [active-claim claims]} (auth/current-authentication request)
        roles (strip-session-role claims)]
    (cond-> {:identity active-claim}
            (seq roles) (assoc :roles (vec roles)))))

(def valid-ttl? (every-pred int? pos?))

;;
;; convert template to credential: loads and validates the given SSH public key
;; provides attributes about the key.
;;
(defmethod p/tpl->credential tpl/credential-subtype
  [{:keys [subtype method ttl acl]} request]
  (let [[secret-key digest] (key-utils/generate)
        resource (cond-> {:resource-type p/resource-type
                          :subtype       subtype
                          :method        method
                          :digest        digest
                          :claims        (extract-claims request)
                          :acl           acl}
                         (valid-ttl? ttl) (assoc :expiry (u/ttl->timestamp ttl)))]
    [{:secret-key secret-key} resource]))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::api-key/schema))
(defmethod p/validate-subtype tpl/credential-subtype
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::api-key/schema-create))
(defmethod p/create-validate-subtype tpl/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; multimethod for edition
;;
(defmethod p/special-edit tpl/credential-subtype
  [resource {:keys [nuvla/authn] :as request}]
  (if (acl-resource/is-admin? authn)
    resource
    (dissoc resource :claims)))

;;
;; initialization: no schema for this parent resource
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::api-key/schema))


(defn initialize
  []
  (md/register resource-metadata))
