(ns sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-item
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-enumeration :as enumeration]
    [sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-range :as range]
    [sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-single-value :as single-value]
    [sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-unit :as unit]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::item-spec (s/or :unit ::unit/unit
                         :single-value ::single-value/single-value
                         :range ::range/range
                         :enumeration ::enumeration/enumeration))


(s/def ::item (s/map-of keyword? ::item-spec :min-count 1))


(s/def ::collection-item (su/only-keys :req-un [::item]))
