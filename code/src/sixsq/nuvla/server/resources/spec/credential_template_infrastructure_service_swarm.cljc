(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-swarm
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-swarm :as cred-infra-service-swarm]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def keys-spec {:req-un [::cred-infra-service-swarm/ca
                         ::cred-infra-service-swarm/cert
                         ::cred-infra-service-swarm/key]})


;; Defines the contents of the swarm credential template resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     keys-spec))


;; Defines the contents of the swarm credential template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ct/template-keys-spec
                                  keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
