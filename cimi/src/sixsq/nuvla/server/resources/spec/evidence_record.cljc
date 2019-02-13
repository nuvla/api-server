(ns sixsq.nuvla.server.resources.spec.evidence-record
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::class ::cimi-core/nonblank-string)
(s/def ::end-time ::cimi-core/timestamp)
(s/def ::start-time ::cimi-core/timestamp)
(s/def ::plan-id ::cimi-core/nonblank-string)
(s/def ::passed boolean?)
(s/def ::log (s/coll-of string?))

(def evidence-record-spec {:req-un [::end-time ::start-time ::plan-id ::passed]
                           :opt-un [::log ::class]})

(s/def ::evidence-record
  (su/constrained-map keyword? any?
                      cimi-common/common-attrs
                      evidence-record-spec))
