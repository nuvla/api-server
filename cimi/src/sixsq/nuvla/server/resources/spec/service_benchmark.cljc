(ns sixsq.nuvla.server.resources.spec.service-benchmark
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::credentials (s/coll-of ::cimi-common/resource-link))
(s/def ::serviceOffer ::cimi-common/resource-link)

(s/def ::service-benchmark
  (su/constrained-map keyword? any?
                      cimi-common/common-attrs
                      {:req-un [::credentials
                                ::serviceOffer]}))
