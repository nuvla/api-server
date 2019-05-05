(ns sixsq.nuvla.server.resources.spec.nuvlabox-record
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::state #{"new" "activated" "quarantined"})
(s/def ::macAddress ::core/nonblank-string)
(s/def ::owner ::common/resource-link)
(s/def ::vmCidr ::core/nonblank-string)
(s/def ::lanCidr ::core/nonblank-string)
(s/def ::wifiSSID ::core/nonblank-string)
(s/def ::wifiPassword ::core/nonblank-string)
(s/def ::rootPassword ::core/nonblank-string)
(s/def ::loginUsername ::core/nonblank-string)
(s/def ::loginPassword ::core/nonblank-string)
(s/def ::cloudPassword ::core/nonblank-string)
(s/def ::refreshInterval pos-int?)
(s/def ::info ::common/resource-link)
(s/def ::location ::core/nonblank-string)
(s/def ::supplier ::core/nonblank-string)
(s/def ::organization ::core/nonblank-string)
(s/def ::formFactor ::core/nonblank-string)
(s/def ::manufacturerSerialNumber ::core/nonblank-string)
(s/def ::firmwareVersion ::core/nonblank-string)
(s/def ::hardwareType ::core/nonblank-string)
(s/def ::comment ::core/nonblank-string)
(s/def ::OSVersion ::core/nonblank-string)
(s/def ::hwRevisionCode ::core/nonblank-string)
(s/def ::CPU pos-int?)
(s/def ::RAM pos-int?)


(s/def ::nuvlabox-record
  (su/only-keys-maps common/common-attrs
                     {:req-un [::state
                               ::macAddress
                               ::owner
                               ::refreshInterval]
                      :opt-un [::info
                               ::location
                               ::supplier
                               ::organization
                               ::manufacturerSerialNumber
                               ::firmwareVersion
                               ::hardwareType
                               ::formFactor
                               ::wifiSSID
                               ::wifiPassword
                               ::rootPassword
                               ::loginUsername
                               ::loginPassword
                               ::cloudPassword
                               ::comment
                               ::vmCidr
                               ::lanCidr
                               ::OSVersion
                               ::hwRevisionCode
                               ::CPU
                               ::RAM]}))

