(ns sixsq.nuvla.server.resources.spec.nuvlabox-record
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::connector ::cimi-common/resource-link)
(s/def ::state #{"new" "activated" "quarantined"})
(s/def ::macAddress ::cimi-core/nonblank-string)
(s/def ::owner ::cimi-common/resource-link)
(s/def ::sslCA ::cimi-core/nonblank-string)
(s/def ::sslCert ::cimi-core/nonblank-string)
(s/def ::sslKey ::cimi-core/nonblank-string)
(s/def ::vmCidr ::cimi-core/nonblank-string)
(s/def ::lanCidr ::cimi-core/nonblank-string)
(s/def ::vpnIP ::cimi-core/nonblank-string)
(s/def ::vpnServerIP ::cimi-core/nonblank-string)
(s/def ::vpnServerPort pos-int?)
(s/def ::user ::cimi-common/resource-link)
(s/def ::wifiSSID ::cimi-core/nonblank-string)
(s/def ::wifiPassword ::cimi-core/nonblank-string)
(s/def ::rootPassword ::cimi-core/nonblank-string)
(s/def ::loginUsername ::cimi-core/nonblank-string)
(s/def ::loginPassword ::cimi-core/nonblank-string)
(s/def ::cloudPassword ::cimi-core/nonblank-string)
(s/def ::refreshInterval pos-int?)
(s/def ::info ::cimi-common/resource-link)
(s/def ::location ::cimi-core/nonblank-string)
(s/def ::supplier ::cimi-core/nonblank-string)
(s/def ::organization ::cimi-core/nonblank-string)
(s/def ::formFactor ::cimi-core/nonblank-string)
(s/def ::manufacturerSerialNumber ::cimi-core/nonblank-string)
(s/def ::firmwareVersion ::cimi-core/nonblank-string)
(s/def ::hardwareType ::cimi-core/nonblank-string)
(s/def ::comment ::cimi-core/nonblank-string)
(s/def ::OSVersion ::cimi-core/nonblank-string)
(s/def ::hwRevisionCode ::cimi-core/nonblank-string)
(s/def ::CPU pos-int?)
(s/def ::RAM pos-int?)
(s/def ::notificationEmails (s/coll-of ::cimi-core/email))
(s/def ::notificationDelay pos-int?)


(s/def ::nuvlabox-record
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::state
                               ::macAddress
                               ::owner
                               ::refreshInterval]
                      :opt-un [::connector
                               ::info
                               ::user
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
                               ::sslCA
                               ::sslCert
                               ::sslKey
                               ::vmCidr
                               ::lanCidr
                               ::vpnIP
                               ::vpnServerIP
                               ::vpnServerPort
                               ::OSVersion
                               ::hwRevisionCode
                               ::notificationEmails
                               ::notificationDelay
                               ::CPU
                               ::RAM]}))

