(ns com.sixsq.slipstream.ssclj.resources.spec.module-image
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as c]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.resources.spec.module :as module]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::commit ::cimi-core/nonblank-string)
(s/def ::author ::cimi-core/nonblank-string)

(s/def ::cpu nat-int?)
(s/def ::ram nat-int?)
(s/def ::disk nat-int?)
(s/def ::volatileDisk nat-int?)
(s/def ::networkType #{"public" "private"})

(s/def ::os #{"CentOS" "Debian" "Fedora" "OpenSuSE" "RedHat" "SLES" "Ubuntu" "Windows" "Other"})

(s/def ::connector keyword?)

(s/def ::loginUser ::cimi-core/nonblank-string)
(s/def ::sudo boolean?)

(s/def ::imageID ::cimi-core/nonblank-string)

(s/def ::imageIDs (s/map-of ::connector ::imageID))

(s/def ::connectors (s/nilable (s/coll-of ::connector :min-count 1 :kind vector?)))

(s/def ::relatedImage ::module/link)


(def module-image-keys-spec (su/merge-keys-specs [c/common-attrs
                                                  {:req-un [::os
                                                            ::loginUser
                                                            ::networkType
                                                            ::author]
                                                   :opt-un [::imageIDs
                                                            ::sudo
                                                            ::relatedImage
                                                            ::cpu
                                                            ::ram
                                                            ::disk
                                                            ::volatileDisk
                                                            ::commit]}]))

(s/def ::module-image (su/only-keys-maps module-image-keys-spec))
