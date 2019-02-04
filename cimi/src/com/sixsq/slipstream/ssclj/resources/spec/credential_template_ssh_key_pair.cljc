(ns com.sixsq.slipstream.ssclj.resources.spec.credential-template-ssh-key-pair
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common-namespaces :as common-ns]
    [com.sixsq.slipstream.ssclj.resources.spec.credential-template :as ps]
    [com.sixsq.slipstream.ssclj.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::size
  (-> (st/spec pos-int?)
      (assoc :name "size"
             :json-schema/name "size"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "integer"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "size"
             :json-schema/description "size of SSH key to generate"
             :json-schema/help "number of bits in the generated SSH key"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::algorithm
  (-> (st/spec #{"rsa" "dsa"})
      (assoc :name "algorithm"
             :json-schema/name "algorithm"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "algorithm"
             :json-schema/description "SSH key generation algorithm"
             :json-schema/help "SSH key generation algorithm to use, either 'rsa' or 'dsa'"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def credential-template-keys-spec
  {:opt-un [::size
            ::algorithm]})

(def credential-template-create-keys-spec
  {:opt-un [::size
            ::algorithm]})

;; Defines the contents of the ssh-key-pair CredentialTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     credential-template-keys-spec))

;; Defines the contents of the ssh-key-pair template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :credentialTemplate here.
(s/def ::credentialTemplate
  (su/only-keys-maps ps/template-keys-spec
                     credential-template-create-keys-spec))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::credentialTemplate]}))
