(ns sixsq.nuvla.server.resources.spec.deployment-parameter
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::deployment ::cimi-common/resource-link)

(s/def ::nodeID ::cimi-core/token)

(s/def ::name ::cimi-core/token)

(s/def ::value ::cimi-core/nonblank-string)

(def deployment-parameter-keys-spec
  (su/merge-keys-specs [cimi-common/common-attrs
                        {:req-un [::deployment ::name]
                         :opt-un [::nodeID ::value]}]))

(s/def ::deployment-parameter (su/only-keys-maps deployment-parameter-keys-spec))
