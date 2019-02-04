(ns com.sixsq.slipstream.ssclj.resources.spec.virtual-machine-mapping
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as cimi-common]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

(s/def ::cloud ::cimi-core/nonblank-string)
(s/def ::instanceID ::cimi-core/nonblank-string)

(s/def ::runUUID ::cimi-core/nonblank-string)
(s/def ::owner ::cimi-core/nonblank-string)
(s/def ::serviceOffer ::cimi-common/resource-link)

(def virtual-machine-mapping-specs {:req-un [::cloud
                                             ::instanceID]
                                    :opt-un [::runUUID
                                             ::owner
                                             ::serviceOffer]})

(def virtual-machine-mapping-keys-spec (su/merge-keys-specs [cimi-common/common-attrs
                                                             virtual-machine-mapping-specs]))

(s/def ::virtual-machine-mapping (su/only-keys-maps virtual-machine-mapping-keys-spec))
