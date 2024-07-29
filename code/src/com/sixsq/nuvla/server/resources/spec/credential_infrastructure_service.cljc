(ns com.sixsq.nuvla.server.resources.spec.credential-infrastructure-service
  (:require
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [com.sixsq.nuvla.server.util.spec :as su]))


(def credential-service-keys-spec
  (su/merge-keys-specs [ps/credential-keys-spec
                        {:req-un [::common/parent]}]))      ;;; an id pointing to an infrastructure service
