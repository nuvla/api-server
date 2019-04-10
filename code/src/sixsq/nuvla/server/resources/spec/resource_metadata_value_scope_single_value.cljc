(ns sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-single-value
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::units ::core/token)


(s/def ::value ::core/scalar)


(s/def ::single-value (su/only-keys :req-un [::value]
                                    :opt-un [::units]))
