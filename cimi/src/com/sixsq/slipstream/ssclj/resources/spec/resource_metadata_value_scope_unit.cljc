(ns com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-value-scope-unit
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::units ::cimi-core/token)


(s/def ::unit (su/only-keys :req-un [::units]))
