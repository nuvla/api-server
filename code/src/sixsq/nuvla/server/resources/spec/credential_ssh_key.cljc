(ns sixsq.nuvla.server.resources.spec.credential-ssh-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-ssh-key :as ssh-key]
    [sixsq.nuvla.server.util.spec :as su]))


(def credential-keys-spec {:req-un [::ssh-key/public-key]
                           :opt-un [::ssh-key/private-key]})

(s/def ::schema
  (su/only-keys-maps [ps/credential-keys-spec
                      credential-keys-spec]))


;; multiple methods to create an ssh public key, so multiple schemas
(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::ssh-key/template]}))
