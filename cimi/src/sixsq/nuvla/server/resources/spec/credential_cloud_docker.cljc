(ns sixsq.nuvla.server.resources.spec.credential-cloud-docker
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.resources.spec.credential-template-cloud :as ctc]
    [sixsq.nuvla.server.resources.spec.credential-template-cloud-docker :as docker-tpl]
    [sixsq.nuvla.server.util.spec :as su]))

(def credential-keys-spec ctc/credential-template-cloud-keys-spec)

(s/def ::schema
  (su/only-keys-maps cred/credential-keys-spec
                     credential-keys-spec))

(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::docker-tpl/template]}))
