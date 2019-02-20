(ns sixsq.nuvla.server.resources.spec.infrastructure-personal
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.infrastructure :as infra]
    [sixsq.nuvla.server.resources.spec.infrastructure-template :as ps]
    [sixsq.nuvla.server.resources.spec.infrastructure-template-personal :as personal]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::schema
  (su/only-keys-maps infra/infrastructure-keys-spec))

;; multiple methods to create an ssh public key, so multiple schemas
(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::personal/template]}))
