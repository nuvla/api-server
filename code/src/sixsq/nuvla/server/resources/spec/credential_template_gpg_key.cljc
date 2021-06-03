(ns sixsq.nuvla.server.resources.spec.credential-template-gpg-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::public-key
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "public-key"
             :json-schema/type "string"
             :json-schema/display-name "Public Key"
             :json-schema/description "GPG public key"

             :json-schema/order 20)))

(s/def ::private-key
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "private-key"
           :json-schema/type "string"
           :json-schema/display-name "Private Key"
           :json-schema/description "GPG Private key"

           :json-schema/order 21)))


(def credential-template-keys-spec
  {:opt-un [::public-key
            ::private-key]})

(def credential-template-create-keys-spec
  {:opt-un [::public-key
            ::private-key]})

;; Defines the contents of the GPG CredentialTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     credential-template-keys-spec))

;; Defines the contents of the GPG template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  credential-template-create-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
