(ns sixsq.nuvla.server.resources.spec.credential-template-cloud-docker
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.resources.spec.credential-template-cloud :as ctc]
    [sixsq.nuvla.server.util.spec :as su]))

;; Defines the contents of the docker credential template resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     ctc/credential-template-create-keys-spec))

;; Defines the contents of the docker credential template used in a create resource.
(s/def ::template
  (su/only-keys-maps ct/template-keys-spec
                     ctc/credential-template-create-keys-spec))

(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
