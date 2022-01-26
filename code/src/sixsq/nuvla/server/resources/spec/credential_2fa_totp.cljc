(ns sixsq.nuvla.server.resources.spec.credential-2fa-totp
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-2fa-totp :as
     tmpl-totp]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::schema
  (su/only-keys-maps ps/credential-keys-spec
                     tmpl-totp/credential-template-create-keys-spec))
