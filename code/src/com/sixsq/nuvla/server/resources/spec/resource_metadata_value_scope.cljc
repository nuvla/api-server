(ns com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope
  "schema definitions for the 'value-scope' field of an attribute in a
   resource-metadata resource"
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-enumeration :as enumeration]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-item :as item]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-range :as range]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-single-value :as single-value]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-unit :as unit]))


(s/def ::value-scope (s/or :unit ::unit/unit
                           :single-value ::single-value/single-value
                           :range ::range/range
                           :enumeration ::enumeration/enumeration
                           :item ::item/collection-item))

