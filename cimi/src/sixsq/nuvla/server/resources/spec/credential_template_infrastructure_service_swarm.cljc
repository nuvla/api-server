(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-swarm
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service :as ct-infra-service]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-swarm :as cred-infra-service-swarm]
    [sixsq.nuvla.server.util.spec :as su]))


(def keys-spec {:req-un [::cred-infra-service-swarm/ca
                         ::cred-infra-service-swarm/cert
                         ::cred-infra-service-swarm/key]})


;; Defines the contents of the swarm credential template resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     ct-infra-service/credential-template-service-keys-spec
                     keys-spec))


;; Defines the contents of the swarm credential template used in a create resource.
(s/def ::template
  (su/only-keys-maps ct/template-keys-spec
                     ct-infra-service/credential-template-service-create-keys-spec
                     keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
