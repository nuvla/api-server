(ns com.sixsq.nuvla.server.resources.credential-gpg-key
  "
This represents a GPG key.

```json
{
  \"template\" : {
                   \"href\" : \"credential-template/generate-gpg-key\",
                   \"public-key\" : \"-----BEGIN PGP PUBLIC KEY BLOCK-----\n\",
                   \"private-key\": \"-----BEGIN PGP PRIVATE KEY BLOCK-----\n\"
                 }
}
```

When successful, the above command will return a 201 (created) status, a
'location' header with the created credential resource, and a JSON document
containing the plain text secret.

"
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.credential :as p]
    [com.sixsq.nuvla.server.resources.credential-template-gpg-key :as tpl]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.credential-gpg-key :as gpg-key]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


;;
;; convert template to credential: loads the given GPG key
;; provides attributes about the key.
;;
(defmethod p/tpl->credential tpl/credential-subtype
  [{:keys [subtype method public-key private-key acl]} _request]
  (let [resource (cond-> {:resource-type p/resource-type
                          :subtype       subtype
                          :method        method
                          :acl           acl
                          :public-key    public-key}
                         (string? private-key) (assoc :private-key private-key))
        return  (cond-> {:public-key public-key}
                  (string? private-key) (assoc :private-key private-key))]
    [return resource]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::gpg-key/schema))
(defmethod p/validate-subtype tpl/credential-subtype
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::gpg-key/schema-create))
(defmethod p/create-validate-subtype tpl/credential-subtype
  [resource]
  (create-validate-fn resource))

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::gpg-key/schema))


(defn initialize
  []
  (md/register resource-metadata))
