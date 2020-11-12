(ns sixsq.nuvla.server.resources.spec.infrastructure-service
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-coe :as infra-coe]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic
     :as infra-service-tpl-gen]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::schema
  (su/only-keys-maps infra-service-tpl/resource-keys-spec
                     infra-service-tpl-gen/service-template-keys-spec
                     infra-coe/service-keys-spec))
