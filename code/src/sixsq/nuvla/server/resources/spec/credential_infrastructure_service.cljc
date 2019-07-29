(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def credential-service-keys-spec
  (su/merge-keys-specs [cred/credential-keys-spec
                        {:req-un [::common/parent]}]))      ;;; an id pointing to an infrastructure service
