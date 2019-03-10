(ns sixsq.nuvla.server.resources.spec.data-record-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(def prefix-regex #"^[a-z]([a-z0-9-]*[a-z0-9])?$")
(s/def ::prefix (s/and string? #(re-matches prefix-regex %)))


(s/def ::key ::cimi-core/nonblank-string)


(s/def ::type ::cimi-core/nonblank-string)


(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::cimi-common/name           ;; name is required
                               ::cimi-common/description    ;; description is required
                               ::prefix
                               ::key
                               ::type]}))
