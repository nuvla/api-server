(ns com.sixsq.nuvla.server.resources.spec.credential-totp-2fa
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [com.sixsq.nuvla.server.resources.spec.credential-template-totp-2fa :as
     tmpl-totp]
    [com.sixsq.nuvla.server.util.spec :as su]))


(s/def ::schema
  (su/only-keys-maps ps/credential-keys-spec
                     tmpl-totp/credential-template-create-keys-spec))
