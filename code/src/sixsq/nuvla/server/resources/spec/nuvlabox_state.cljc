(ns sixsq.nuvla.server.resources.spec.nuvlabox-state
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::nextCheck ::cimi-core/timestamp)

(s/def ::nuvlabox ::cimi-common/resource-link)

(s/def ::busy boolean?)
(s/def ::vendor-id ::cimi-core/nonblank-string)
(s/def ::device-id ::cimi-core/nonblank-string)
(s/def ::bus-id ::cimi-core/nonblank-string)
(s/def ::product-id ::cimi-core/nonblank-string)
(s/def ::description string?)


(s/def ::usb-info (su/only-keys :req-un [::busy
                                         ::vendor-id
                                         ::device-id
                                         ::bus-id
                                         ::product-id
                                         ::description]))

(s/def ::capacity int?)
(s/def ::used int?)
(s/def ::disk-name ::cimi-core/nonblank-string)

(s/def ::memory-info (su/only-keys :req-un [::capacity ::used]))

(s/def ::state #{"unknown" "online" "offline"})

(s/def ::usb (s/coll-of ::usb-info))
(s/def ::cpu nat-int?)
(s/def ::ram ::memory-info)
(s/def ::disks (su/constrained-map keyword? ::memory-info))
(s/def ::mutableWifiPassword ::cimi-core/nonblank-string)
(s/def ::swarmNodeId ::cimi-core/nonblank-string)
(s/def ::swarmManagerId (s/nilable (s/coll-of ::cimi-core/nonblank-string)) )
(s/def ::swarmManagerToken ::cimi-core/nonblank-string)
(s/def ::swarmNode ::cimi-core/nonblank-string)
(s/def ::leader? boolean?)
(s/def ::swarmWorkerToken ::cimi-core/nonblank-string)
(s/def ::tlsCA ::cimi-core/nonblank-string)
(s/def ::tlsCert ::cimi-core/nonblank-string)
(s/def ::tlsKey ::cimi-core/nonblank-string)

(s/def ::nuvlabox-state
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::nuvlabox
                               ::nextCheck
                               ::cpu
                               ::ram
                               ::disks
                               ::usb]
                      :opt-un [::state
                               ::mutableWifiPassword
                               ::swarmNodeId
                               ::swarmManagerToken
                               ::swarmNode
                               ::swarmManagerId
                               ::leader?
                               ::swarmWorkerToken
                               ::tlsCA
                               ::tlsCert
                               ::tlsKey]}))
