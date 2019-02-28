(ns sixsq.nuvla.server.resources.spec.data-record
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::href ::cimi-core/resource-href)


(s/def ::infrastructure-service (su/only-keys :req-un [::href]))


(s/def ::schema
  (su/constrained-map keyword? any?
                      c/common-attrs
                      {:req-un [::infrastructure-service]}))
