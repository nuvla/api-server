(ns sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-unit
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::units ::core/token)


(s/def ::unit (su/only-keys :req-un [::units]))
