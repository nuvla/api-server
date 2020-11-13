(ns sixsq.nuvla.server.resources.spec.credential-swarm-token
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-swarm-token :as swarm-token-tpl]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::schema
  (su/only-keys-maps ps/credential-keys-spec
                     swarm-token-tpl/credential-template-create-keys-spec))
