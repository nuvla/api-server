(ns sixsq.nuvla.server.resources.spec.module-component
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.container :as container]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::author ::cimi-core/nonblank-string)


(s/def ::commit ::cimi-core/nonblank-string)


(s/def ::architecture ::cimi-core/nonblank-string)



;; this is a 'friendly' name for a given URL, intended mainly for display
(s/def ::url-name ::cimi-core/nonblank-string)


;; pattern, e.g. "https://${host}:${port-443}/some/path", is used to create URL from dynamic info
(s/def ::url-pattern ::cimi-core/nonblank-string)


(s/def ::url-tuple (s/tuple ::url-name ::url-pattern))


(s/def ::urls (s/coll-of ::url-tuple :min-count 1 :kind vector?))


(s/def ::name ::cimi-core/nonblank-string)


(s/def ::description ::cimi-core/nonblank-string)


(s/def ::parameter (su/only-keys :req-un [::name ::description]))


(s/def ::output-parameters (s/coll-of ::parameter :kind vector?))


(def module-component-keys-spec (su/merge-keys-specs [c/common-attrs
                                                      {:req-un [::author
                                                                ::architecture
                                                                ::container/image]
                                                       :opt-un [::commit
                                                                ::container/ports
                                                                ::container/mounts
                                                                ::urls
                                                                ::output-parameters]}]))

(s/def ::module-component (su/only-keys-maps module-component-keys-spec))
