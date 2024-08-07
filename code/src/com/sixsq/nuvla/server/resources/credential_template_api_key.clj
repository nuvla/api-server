(ns com.sixsq.nuvla.server.resources.credential-template-api-key
  "
Used to create an API key-secret pair that allows the holder of the secret to
access the server via the API. The credential can optionally be limited in
time. The rights associated with the key-secret pair are taken from the
`session` that created the key-secret pair.
"
  (:require
    [com.sixsq.nuvla.auth.utils.acl :as acl-utils]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.credential-template :as p]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.credential-template-api-key :as ct-api-key]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "api-key")


(def ^:const resource-name "API Key")


(def ^:const resource-url credential-subtype)


(def ^:const method "generate-api-key")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const resource
  {:subtype           credential-subtype
   :method            method
   :name              "Generate API Key"
   :description       "generates an API key and stores hash"
   :ttl               0
   :acl               resource-acl
   :resource-metadata "resource-metadata/credential-template-api-key"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-api-key/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::ct-api-key/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::ct-api-key/schema-create "create"))


(defn initialize
  []
  (p/register resource)
  (md/register resource-metadata)
  (md/register resource-metadata-create))


