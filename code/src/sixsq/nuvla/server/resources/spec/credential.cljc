(ns sixsq.nuvla.server.resources.spec.credential
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-api-key :as api-key]
    [sixsq.nuvla.server.resources.spec.credential-hashed-password :as hashed-password]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-coe :as coe]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-vpn :as vpn]
    [sixsq.nuvla.server.resources.spec.credential-ssh-key :as ssh-key]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-swarm-token :as swarm-token]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::schema (su/only-keys-maps
                  ps/resource-keys-spec
                  api-key/credential-keys-spec
                  hashed-password/credential-keys-spec
                  infra-service/credential-service-keys-spec
                  coe/credential-keys-spec
                  vpn/credential-keys-spec
                  ssh-key/credential-keys-spec
                  swarm-token/credential-template-create-keys-spec))