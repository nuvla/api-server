(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service
  (:require
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.util.spec :as su]))


(def credential-service-keys-spec
  (su/merge-keys-specs [cred/credential-keys-spec
                        {:req-un [::common/parent]}]))      ;;; an id pointing to an infrastructure service
