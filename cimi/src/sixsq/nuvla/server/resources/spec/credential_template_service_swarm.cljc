(ns sixsq.nuvla.server.resources.spec.credential-template-service-swarm
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.resources.spec.credential-template-service :as ct-service]
    [sixsq.nuvla.server.resources.spec.credential-service-swarm :as cred-service-swarm]
    [sixsq.nuvla.server.util.spec :as su]))


(def keys-spec {:req-un [::cred-service-swarm/ca
                         ::cred-service-swarm/cert
                         ::cred-service-swarm/key]})


;; Defines the contents of the swarm credential template resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     ct-service/credential-template-service-keys-spec
                     keys-spec))


;; Defines the contents of the swarm credential template used in a create resource.
(s/def ::template
  (su/only-keys-maps ct/template-keys-spec
                     ct-service/credential-template-service-create-keys-spec
                     keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
