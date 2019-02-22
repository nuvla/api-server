(ns sixsq.nuvla.server.resources.spec.credential-template-service-docker
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.resources.spec.credential-template-service :as cred-tpl-service]
    [sixsq.nuvla.server.resources.spec.credential-template-service-docker :as cred-service-docker]
    [sixsq.nuvla.server.util.spec :as su]))


(def keys-spec {:req-un [::cred-service-docker/ca
                         ::cred-service-docker/cert
                         ::cred-service-docker/key]})


;; Defines the contents of the docker credential template resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     cred-tpl-service/credential-template-service-keys-spec
                     keys-spec))


;; Defines the contents of the docker credential template used in a create resource.
(s/def ::template
  (su/only-keys-maps ct/template-keys-spec
                     cred-tpl-service/credential-template-service-create-keys-spec
                     keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
