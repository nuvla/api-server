(ns com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-value-scope-single-value
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::units ::cimi-core/token)


(s/def ::value ::cimi-core/scalar)


(s/def ::single-value (su/only-keys :req-un [::value]
                                    :opt-un [::units]))
