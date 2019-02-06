(ns sixsq.nuvla.server.resources.spec.module-component
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.module :as module]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::commit ::cimi-core/nonblank-string)
(s/def ::author ::cimi-core/nonblank-string)

(s/def ::parentModule ::module/link)

(s/def ::cpu nat-int?)
(s/def ::ram nat-int?)
(s/def ::disk nat-int?)
(s/def ::volatileDisk nat-int?)
(s/def ::networkType #{"public" "private"})
(s/def ::ports (s/coll-of ::cimi-core/nonblank-string :min-count 1 :kind vector?))
(s/def ::mounts (s/coll-of ::cimi-core/nonblank-string :min-count 1 :kind vector?))

;; parameter keywords are used in components and application parameter mappings
(def ^:const parameter-name-regex #"^[a-zA-Z0-9]+([-_\.:][a-zA-Z0-9]*)*$")
(s/def ::parameter (s/and string? #(re-matches parameter-name-regex %)))

(s/def ::description ::cimi-core/nonblank-string)
(s/def ::value ::cimi-core/nonblank-string)

(s/def ::parameter-map (su/only-keys :req-un [::parameter]
                                     :opt-un [::description ::value]))

(s/def ::parameters (s/coll-of ::parameter-map :min-count 1 :kind vector?))

(s/def ::inputParameters ::parameters)
(s/def ::outputParameters ::parameters)

(s/def ::target ::cimi-core/nonblank-string)
(s/def ::package-list (s/coll-of ::cimi-core/nonblank-string :min-count 1 :kind vector?))

(s/def ::preinstall ::target)
(s/def ::packages ::package-list)
(s/def ::postinstall ::target)
(s/def ::deployment ::target)
(s/def ::reporting ::target)
(s/def ::onVmAdd ::target)
(s/def ::onVmRemove ::target)
(s/def ::prescale ::target)
(s/def ::postscale ::target)

(s/def ::targets (su/only-keys :opt-un [::preinstall
                                        ::packages
                                        ::postinstall
                                        ::deployment
                                        ::reporting
                                        ::onVmAdd
                                        ::onVmRemove
                                        ::prescale
                                        ::postscale]))


(def module-component-keys-spec (su/merge-keys-specs [c/common-attrs
                                                      {:req-un [::parentModule
                                                                ::networkType
                                                                ::outputParameters
                                                                ::author]
                                                       :opt-un [::inputParameters
                                                                ::cpu
                                                                ::ram
                                                                ::disk
                                                                ::volatileDisk
                                                                ::ports
                                                                ::mounts
                                                                ::targets
                                                                ::commit]}]))

(s/def ::module-component (su/only-keys-maps module-component-keys-spec))
