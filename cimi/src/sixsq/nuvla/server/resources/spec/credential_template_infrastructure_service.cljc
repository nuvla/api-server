(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service
  (:require
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service :as cred-infra-service]))


(def credential-template-service-keys-spec {:opt-un [::cred-infra-service/services]})


(def credential-template-service-create-keys-spec {:opt-un [::cred-infra-service/services]})
