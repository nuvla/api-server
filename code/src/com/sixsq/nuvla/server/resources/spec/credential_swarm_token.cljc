(ns com.sixsq.nuvla.server.resources.spec.credential-swarm-token
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [com.sixsq.nuvla.server.resources.spec.credential-template-swarm-token :as swarm-token-tpl]
    [com.sixsq.nuvla.server.util.spec :as su]))


(s/def ::schema
  (su/only-keys-maps ps/credential-keys-spec
                     swarm-token-tpl/credential-template-create-keys-spec))
