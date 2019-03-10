(ns sixsq.nuvla.server.resources.spec.data-record-key-prefix
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(def prefix-regex #"^[a-z]([a-z0-9-]*[a-z0-9])?$")


(s/def ::prefix (s/and string? #(re-matches prefix-regex %)))


(s/def ::schema
  (su/only-keys-maps c/common-attrs
                     {:req-un [::prefix
                               ::cimi-core/uri]}))
