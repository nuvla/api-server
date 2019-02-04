(ns com.sixsq.slipstream.ssclj.resources.spec.virtual-machine
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as cimi-common]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

(s/def ::href ::cimi-core/nonblank-string)
(s/def ::credentials (s/coll-of (s/keys :req-un [::href]))) ; TODO: switch to ::cimi-common/resource-links when credential resource will be in use
(s/def ::instanceID ::cimi-core/nonblank-string)
(s/def ::state ::cimi-core/nonblank-string)
(s/def ::ip ::cimi-core/nonblank-string)
(s/def ::connector ::cimi-common/resource-link)
(s/def ::serviceOffer ::cimi-common/resource-link)
(s/def ::deployment ::cimi-common/resource-link)
(s/def ::currency ::cimi-core/nonblank-string)
(s/def ::billable boolean?)


(def virtual-machine-specs {:req-un [::credentials
                                     ::instanceID
                                     ::connector
                                     ::state]
                            :opt-un [::deployment
                                     ::serviceOffer
                                     ::ip
                                     ::currency
                                     ::billable]})

(def virtual-machine-keys-spec (su/merge-keys-specs [cimi-common/common-attrs
                                                     virtual-machine-specs]))

(s/def ::virtual-machine (su/only-keys-maps virtual-machine-keys-spec))
