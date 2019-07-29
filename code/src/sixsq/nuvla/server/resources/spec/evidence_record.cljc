(ns sixsq.nuvla.server.resources.spec.evidence-record
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::class ::core/nonblank-string)


(s/def ::end-time ::core/timestamp)


(s/def ::start-time ::core/timestamp)


(s/def ::plan-id ::core/nonblank-string)


(s/def ::passed boolean?)


(s/def ::log (s/coll-of string?))


(def evidence-record-spec {:req-un [::end-time ::start-time ::plan-id ::passed]
                           :opt-un [::log ::class]})


(s/def ::schema
  (su/constrained-map keyword? any?
                      common/common-attrs
                      evidence-record-spec))
