(ns com.sixsq.nuvla.server.resources.spec.credential-gpg-key
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [com.sixsq.nuvla.server.resources.spec.credential-template-gpg-key :as gpg-key]
    [com.sixsq.nuvla.server.util.spec :as su]))


(def credential-keys-spec {:req-un [::gpg-key/public-key]
                           :opt-un [::gpg-key/private-key]})

(s/def ::schema
  (su/only-keys-maps ps/credential-keys-spec
                     credential-keys-spec))

;; multiple methods to create a gpg public key, so multiple schemas
(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::gpg-key/template]}))
