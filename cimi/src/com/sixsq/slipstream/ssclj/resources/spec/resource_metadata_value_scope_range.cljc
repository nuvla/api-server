(ns com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-value-scope-range
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::minimum int?)


(s/def ::maximum int?)


(s/def ::increment int?)


(s/def ::default int?)


(s/def ::units ::cimi-core/token)


(s/def ::range (s/or :both (su/only-keys :req-un [::minimum
                                                  ::maximum]
                                         :opt-un [::increment
                                                  ::default
                                                  ::units])
                     :only-min (su/only-keys :req-un [::minimum]
                                             :opt-un [::increment
                                                      ::default
                                                      ::units])
                     :only-max (su/only-keys :req-un [::maximum]
                                             :opt-un [::increment
                                                      ::default
                                                      ::units])))
