(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service-helm-repo
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service :as cred-infra-service]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-helm-repo :as service]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::schema
  (su/only-keys-maps service/credential-template-keys-spec-req
                     cred-infra-service/credential-service-keys-spec
                     ps/credential-keys-spec))
