(ns sixsq.nuvla.server.resources.spec.service-attribute-namespace
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(def prefix-regex #"^[a-z]([a-z0-9-]*[a-z0-9])?$")


(s/def ::prefix (s/and string? #(re-matches prefix-regex %)))


(s/def ::service-attribute-namespace
  (su/only-keys-maps c/common-attrs
                     {:req-un [::prefix
                               ::cimi-core/uri]}))
