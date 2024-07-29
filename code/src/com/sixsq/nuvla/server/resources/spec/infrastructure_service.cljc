(ns com.sixsq.nuvla.server.resources.spec.infrastructure-service
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.infrastructure-service-template :as infra-service-tpl]
    [com.sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic
     :as infra-service-tpl-gen]
    [com.sixsq.nuvla.server.util.spec :as su]))


(s/def ::schema
  (su/only-keys-maps infra-service-tpl/resource-keys-spec
                     infra-service-tpl-gen/service-template-keys-spec))
