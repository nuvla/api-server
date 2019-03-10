(ns sixsq.nuvla.server.resources.spec.credential-api-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-api-key :as api-key]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::expiry ::cimi-core/timestamp)

(s/def ::digest ::cimi-core/nonblank-string)

(s/def ::identity ::cimi-core/nonblank-string)

(s/def ::roles (s/coll-of ::cimi-core/nonblank-string
                          :kind vector?
                          :into []
                          :min-count 1))

(s/def ::claims (su/only-keys :req-un [::identity]
                              :opt-un [::roles]))

(def credential-keys-spec
  {:req-un [::digest
            ::claims]
   :opt-un [::expiry]})

(s/def ::schema
  (su/only-keys-maps cred/credential-keys-spec
                     credential-keys-spec))

;; multiple methods to create an ssh public key, so multiple schemas
(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::api-key/template]}))
