(ns sixsq.nuvla.server.resources.spec.credential-template-service
  (:require
    [sixsq.nuvla.server.resources.spec.credential-service :as cred-service]))


(def credential-template-service-keys-spec {:req-un [::cred-service/service]})


(def credential-template-service-create-keys-spec {:req-un [::cred-service/service]})
