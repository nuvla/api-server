(ns sixsq.nuvla.server.resources.spec.module-image
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.module :as module]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::author ::cimi-core/nonblank-string)


(s/def ::commit ::cimi-core/nonblank-string)


(s/def ::architecture ::cimi-core/nonblank-string)


(s/def ::image ::cimi-core/nonblank-string)


(s/def ::ports (s/coll-of ::cimi-core/nonblank-string :kind vector?))


(s/def ::related-image ::module/link)


(def module-image-keys-spec (su/merge-keys-specs [c/common-attrs
                                                  {:req-un [::author
                                                            ::architecture
                                                            ::image]
                                                   :opt-un [::commit
                                                            ::ports
                                                            ::related-image]}]))

(s/def ::module-image (su/only-keys-maps module-image-keys-spec))
