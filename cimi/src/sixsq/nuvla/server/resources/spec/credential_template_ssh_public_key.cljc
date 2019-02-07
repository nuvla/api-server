(ns sixsq.nuvla.server.resources.spec.credential-template-ssh-public-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::publicKey
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "publicKey"
             :json-schema/name "publicKey"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "public key"
             :json-schema/description "SSH public key"
             :json-schema/help "SSH public key"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false
             :json-schema/lines 3)))


(def credential-template-keys-spec
  {:req-un [::publicKey]})

(def credential-template-create-keys-spec
  {:req-un [::publicKey]})

;; Defines the contents of the ssh-public-key CredentialTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     credential-template-keys-spec))

;; Defines the contents of the ssh-public-key template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     credential-template-create-keys-spec))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
