(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-coe
  "
Spec for credentials template of Container Orchestration Engine (COE).
"
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-coe :as cred-infra-service-coe]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def keys-spec {:req-un [::cred-infra-service-coe/ca
                         ::cred-infra-service-coe/cert
                         ::cred-infra-service-coe/key]})


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
