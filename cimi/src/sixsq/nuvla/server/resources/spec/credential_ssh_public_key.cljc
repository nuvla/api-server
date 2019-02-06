(ns sixsq.nuvla.server.resources.spec.credential-ssh-public-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template-ssh-key-pair :as ct-ssh-key-pair]
    [sixsq.nuvla.server.resources.spec.credential-template-ssh-public-key :as ct-ssh-public-key]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::algorithm #{"rsa" "dsa"})

(s/def ::fingerprint (s/and string? #(re-matches #"^[0-9a-f]{2}(:[0-9a-f]{2}){15}$" %)))

(s/def ::publicKey ::ct-ssh-public-key/publicKey)

(def credential-keys-spec
  {:req-un [::algorithm
            ::fingerprint
            ::publicKey]})

(s/def ::schema
  (su/only-keys-maps cred/credential-keys-spec
                     credential-keys-spec))

;; multiple methods to create an ssh public key, so multiple schemas
(s/def ::schema-create
  (s/or :import ::ct-ssh-public-key/schema-create
        :generate ::ct-ssh-key-pair/schema-create))
