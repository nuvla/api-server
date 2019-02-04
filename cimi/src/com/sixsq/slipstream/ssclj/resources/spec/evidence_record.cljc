(ns com.sixsq.slipstream.ssclj.resources.spec.evidence-record
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as cimi-common]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::class ::cimi-core/nonblank-string)
(s/def ::endTime ::cimi-core/timestamp)
(s/def ::startTime ::cimi-core/timestamp)
(s/def ::planID ::cimi-core/nonblank-string)
(s/def ::passed boolean?)
(s/def ::log (s/coll-of string?))

(def evidence-record-spec {:req-un [::endTime ::startTime ::planID ::passed]
                           :opt-un [::log ::class]})

(s/def ::evidence-record
  (su/constrained-map keyword? any?
                      cimi-common/common-attrs
                      evidence-record-spec))
