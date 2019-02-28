(ns sixsq.nuvla.server.resources.spec.credential-service-gce
    (:require
      [clojure.spec.alpha :as s]
      [sixsq.nuvla.server.resources.spec.credential :as cred]
      [sixsq.nuvla.server.resources.spec.credential-template :as ps]
      [sixsq.nuvla.server.resources.spec.credential-infrastructure-service :as cred-infra-service]
      [sixsq.nuvla.server.resources.spec.credential-template-service-gce :as service]
      [sixsq.nuvla.server.util.spec :as su]))

(s/def ::schema
  (su/only-keys-maps service/credential-template-keys-spec
                     cred-infra-service/credential-service-keys-spec
                     cred/credential-keys-spec))

;; multiple methods to create an ssh public key, so multiple schemas
(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::service/template]}))
