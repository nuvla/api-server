(ns sixsq.nuvla.server.resources.spec.resource-metadata-value-scope-range
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::minimum int?)


(s/def ::maximum int?)


(s/def ::increment int?)


(s/def ::default int?)


(s/def ::units ::core/token)


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
