(ns sixsq.nuvla.server.resources.spec.data-record-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]))


(def prefix-regex #"^[a-z]([a-z0-9-]*[a-z0-9])?$")
(s/def ::prefix (s/and string? #(re-matches prefix-regex %)))


(s/def ::key ::core/nonblank-string)


(s/def ::subtype ::common/subtype)


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::common/name                ;; name is required
                               ::common/description         ;; description is required
                               ::prefix
                               ::key
                               ::subtype]}))
