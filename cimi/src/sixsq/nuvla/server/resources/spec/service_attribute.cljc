(ns sixsq.nuvla.server.resources.spec.service-attribute
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))

(def prefix-regex #"^[a-z]([a-z0-9-]*[a-z0-9])?$")
(s/def ::prefix (s/and string? #(re-matches prefix-regex %)))

(s/def ::attributeName ::cimi-core/nonblank-string)

(s/def ::type ::cimi-core/nonblank-string)

(s/def ::service-attribute
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::cimi-common/name           ;; name is required
                               ::cimi-common/description    ;; description is required
                               ::prefix
                               ::attributeName
                               ::type]}))
