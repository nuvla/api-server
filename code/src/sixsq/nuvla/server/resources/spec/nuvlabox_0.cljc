(ns sixsq.nuvla.server.resources.spec.nuvlabox-0
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.nuvlabox :as nb]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::state
  (assoc (st/spec #{"NEW" "ACTIVATED" "COMMISSIONED" "DECOMMISSIONING"
                    "DECOMMISSIONED" "ERROR" "SUSPENDED"})
    :name "state"
    :json-schema/type "string"
    :json-schema/description "state of NuvlaBox"

    :json-schema/value-scope {:values ["NEW" "ACTIVATED" "COMMISSIONED"
                                       "DECOMMISSIONING" "DECOMMISSIONED"
                                       "ERROR" "SUSPENDED"]}

    :json-schema/order 10))


(s/def ::vm-cidr
  (assoc (st/spec ::core/nonblank-string)
    :name "vm-cidr"
    :json-schema/display-name "VM CIDR"
    :json-schema/description "network range for virtual machines"

    :json-schema/order 11))


(s/def ::lan-cidr
  (assoc (st/spec ::core/nonblank-string)
    :name "lan-cidr"
    :json-schema/display-name "LAN CIDR"
    :json-schema/description "network range for local area network"

    :json-schema/order 12))


(s/def ::wifi-ssid
  (assoc (st/spec ::core/nonblank-string)
    :name "wifi-ssid"
    :json-schema/display-name "WIFI SSID"
    :json-schema/description "WIFI network identifier"

    :json-schema/order 13))


(s/def ::wifi-password
  (assoc (st/spec ::core/nonblank-string)
    :name "wifi-password"
    :json-schema/display-name "WIFI password"
    :json-schema/description "WIFI network password"

    :json-schema/order 14))


(s/def ::root-password
  (assoc (st/spec ::core/nonblank-string)
    :name "root-password"
    :json-schema/display-name "root password"
    :json-schema/description "root password for NuvlaBox"

    :json-schema/order 15))


(s/def ::login-username
  (assoc (st/spec ::core/nonblank-string)
    :name "login-username"
    :json-schema/display-name "login username"
    :json-schema/description "username to log into NuvlaBox"

    :json-schema/order 16))


(s/def ::login-password
  (assoc (st/spec ::core/nonblank-string)
    :name "login-password"
    :json-schema/display-name "login password"
    :json-schema/description "password to log into NuvlaBox"

    :json-schema/order 17))


(s/def ::cloud-password
  (assoc (st/spec ::core/nonblank-string)
    :name "cloud-password"
    :json-schema/display-name "cloud password"
    :json-schema/description "password for cloud infrastructure"

    :json-schema/order 18))


(s/def ::refresh-interval
  (assoc (st/spec pos-int?)
    :name "refresh-interval"
    :json-schema/display-name "refresh interval"
    :json-schema/description "refresh interval in seconds for state updates"

    :json-schema/order 19))


(s/def ::location
  (assoc (st/spec (s/coll-of number? :min-count 2 :max-count 3))
    :name "location"
    :json-schema/type "geo-point"
    :json-schema/display-name "location"
    :json-schema/description "location [longitude, latitude, altitude] associated with the data"

    :json-schema/order 20))


(s/def ::supplier
  (assoc (st/spec ::core/nonblank-string)
    :name "supplier"
    :json-schema/description "name of hardware supplier"

    :json-schema/order 21))


(s/def ::organization
  (assoc (st/spec ::core/nonblank-string)
    :name "organization"
    :json-schema/description "organization associated with the NuvlaBox"

    :json-schema/order 22))


(s/def ::form-factor
  (assoc (st/spec ::core/nonblank-string)
    :name "form-factor"
    :json-schema/display-name "form factor"
    :json-schema/description "hardware form factor"

    :json-schema/order 23))


(s/def ::manufacturer-serial-number
  (assoc (st/spec ::core/nonblank-string)
    :name "manufacturer-serial-number"
    :json-schema/display-name "manufacturer serial number"
    :json-schema/description "hardware manufacturer serial number"

    :json-schema/order 24))


(s/def ::firmware-version
  (assoc (st/spec ::core/nonblank-string)
    :name "firmware-version"
    :json-schema/display-name "firmware version"
    :json-schema/description "NuvlaBox software firmware version"

    :json-schema/order 25))


(s/def ::hardware-type
  (assoc (st/spec ::core/nonblank-string)
    :name "hardware-type"
    :json-schema/display-name "hardware type"
    :json-schema/description "hardware type of the NuvlaBox"

    :json-schema/order 26))


(s/def ::comment
  (assoc (st/spec ::core/nonblank-string)
    :name "comment"
    :json-schema/description "comment about the NuvlaBox"

    :json-schema/order 27))


(s/def ::os-version
  (assoc (st/spec ::core/nonblank-string)
    :name "os-version"
    :json-schema/display-name "OS version"
    :json-schema/description "operating system version"

    :json-schema/order 28))


(s/def ::hw-revision-code
  (assoc (st/spec ::core/nonblank-string)
    :name "hw-revision-code"
    :json-schema/display-name "hardware revision code"
    :json-schema/description "hardware revision code"

    :json-schema/order 29))


(s/def ::monitored
  (assoc (st/spec boolean?)
    :name "monitored"
    :json-schema/type "boolean"
    :json-schema/description "flag to indicate whether machine should be monitored"

    :json-schema/order 30))


(def infra-srvc-id-regex #"^infrastructure-service/[a-zA-Z0-9-]+$")

(s/def ::vpn-server-id
  (assoc (st/spec (s/and string? #(re-matches infra-srvc-id-regex %)))
    :name "vpn-server-id"
    :json-schema/type "string"
    :json-schema/display-name "vpn server id"
    :json-schema/description "VPN infrastructure service id to connect"
    :json-schema/order 31))


(s/def ::internal-data-gateway-endpoint
  (assoc (st/spec ::core/nonblank-string)
    :name "internal-data-gateway-endpoint"
    :json-schema/display-name "nuvlabox data gateway endpoint"
    :json-schema/description "the endpoint users should connect to, from within the NuvlaBox, to subscribe to the data gateway"
    :json-schema/order 32))


(s/def ::ssh-keys
  (assoc
    (st/spec (s/coll-of ::core/nonblank-string :kind vector?))
    :name "ssh-keys"
    :json-schema/display-name "NuvlaBox SSH keys"
    :json-schema/description "list of SSH keys associated with the NuvlaBox"
    :json-schema/order 33))


(s/def ::capabilities
  (assoc (st/spec (s/coll-of ::core/nonblank-string :kind vector?))
    :name "capabilities"
    :json-schema/display-name "NuvlaBox capabilities"
    :json-schema/description "list of NuvlaBox capabilities"
    :json-schema/order 34))


(s/def ::online
  (assoc (st/spec boolean?)
    :name "online"
    :json-schema/type "boolean"
    :json-schema/description "Indicate if the nuvlabox is connected to Nuvla service"
    :json-schema/server-managed true

    :json-schema/order 35))


(s/def ::inferred-location
  (assoc (st/spec (s/coll-of number? :min-count 2 :max-count 3))
    :name "inferred-location"
    :json-schema/type "geo-point"
    :json-schema/display-name "inferred-location"
    :json-schema/description "location [longitude, latitude, altitude] - dynamically inferred by the NuvlaBox"
    :json-schema/server-managed true

    :json-schema/order 36))

(s/def ::nuvlabox-engine-version
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "nuvlabox-engine-version"
             :json-schema/description "nuvlabox engine release"

             :json-schema/order 37)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb/attributes
                     {:req-un [::state
                               ::refresh-interval]
                      :opt-un [::location
                               ::supplier
                               ::organization
                               ::manufacturer-serial-number
                               ::firmware-version
                               ::hardware-type
                               ::form-factor
                               ::wifi-ssid
                               ::wifi-password
                               ::root-password
                               ::login-username
                               ::login-password
                               ::cloud-password
                               ::comment
                               ::vm-cidr
                               ::lan-cidr
                               ::os-version
                               ::hw-revision-code
                               ::monitored
                               ::vpn-server-id
                               ::internal-data-gateway-endpoint
                               ::ssh-keys
                               ::capabilities
                               ::online
                               ::inferred-location
                               ::nuvlabox-engine-version]}))
