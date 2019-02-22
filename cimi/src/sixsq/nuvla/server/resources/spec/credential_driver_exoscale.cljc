(ns sixsq.nuvla.server.resources.spec.credential-driver-exoscale
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-driver-exoscale :as driver]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::schema
  (su/only-keys-maps cred/credential-keys-spec))

;; multiple methods to create an ssh public key, so multiple schemas
(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::driver/template]}))
