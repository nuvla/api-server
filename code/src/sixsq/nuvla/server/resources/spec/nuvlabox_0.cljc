(ns sixsq.nuvla.server.resources.spec.nuvlabox-0
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.nuvlabox :as nb]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::state
  (-> (st/spec #{"NEW"
                 "ACTIVATED"
                 "COMMISSIONING" "COMMISSIONED"
                 "DECOMMISSIONING" "DECOMMISSIONED"
                 "ERROR"})
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/description "state of NuvlaBox"

             :json-schema/order 10)))


(s/def ::owner
  (-> (st/spec ::common/id)
      (assoc :name "owner"
             :json-schema/type "resource-id"
             :json-schema/description "id of principal (user or group) that owns the NuvlaBox"

             :json-schema/order 11)))


(s/def ::vm-cidr
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "vm-cidr"
             :json-schema/display-name "VM CIDR"
             :json-schema/description "network range for virtual machines"

             :json-schema/order 12)))


(s/def ::lan-cidr
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "lan-cidr"
             :json-schema/display-name "LAN CIDR"
             :json-schema/description "network range for local area network"

             :json-schema/order 13)))


(s/def ::wifi-ssid
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "wifi-ssid"
             :json-schema/display-name "WIFI SSID"
             :json-schema/description "WIFI network identifier"

             :json-schema/order 14)))


(s/def ::wifi-password
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "wifi-password"
             :json-schema/display-name "WIFI password"
             :json-schema/description "WIFI network password"

             :json-schema/order 15)))


(s/def ::root-password
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "root-password"
             :json-schema/display-name "root password"
             :json-schema/description "root password for NuvlaBox"

             :json-schema/order 16)))


(s/def ::login-username
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "login-username"
             :json-schema/display-name "login username"
             :json-schema/description "username to log into NuvlaBox"

             :json-schema/order 17)))


(s/def ::login-password
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "login-password"
             :json-schema/display-name "login password"
             :json-schema/description "password to log into NuvlaBox"

             :json-schema/order 18)))


(s/def ::cloud-password
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "cloud-password"
             :json-schema/display-name "cloud password"
             :json-schema/description "password for cloud infrastructure"

             :json-schema/order 19)))


(s/def ::refresh-interval
  (-> (st/spec pos-int?)
      (assoc :name "refresh-interval"
             :json-schema/display-name "refresh interval"
             :json-schema/description "refresh interval for state updates"

             :json-schema/order 20)))


(s/def ::location
  (-> (st/spec (s/coll-of double? :min-count 2 :max-count 3))
      (assoc :name "location"
             :json-schema/type "geo-point"
             :json-schema/display-name "location"
             :json-schema/description "location [longitude, latitude, altitude] associated with the data"

             :json-schema/order 21)))


(s/def ::supplier
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "supplier"
             :json-schema/description "name of hardware supplier"

             :json-schema/order 22)))


(s/def ::organization
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "organization"
             :json-schema/description "organization associated with the NuvlaBox"

             :json-schema/order 23)))


(s/def ::form-factor
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "form-factor"
             :json-schema/display-name "form factor"
             :json-schema/description "hardware form factor"

             :json-schema/order 24)))


(s/def ::manufacturer-serial-number
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "manufacturer-serial-number"
             :json-schema/display-name "manufacturer serial number"
             :json-schema/description "hardware manufacturer serial number"

             :json-schema/order 25)))


(s/def ::firmware-version
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "firmware-version"
             :json-schema/display-name "firmware version"
             :json-schema/description "NuvlaBox software firmware version"

             :json-schema/order 26)))


(s/def ::hardware-type
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "hardware-type"
             :json-schema/display-name "hardware type"
             :json-schema/description "hardware type of the NuvlaBox"

             :json-schema/order 27)))


(s/def ::comment
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "comment"
             :json-schema/description "comment about the NuvlaBox"

             :json-schema/order 28)))


(s/def ::os-version
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "os-version"
             :json-schema/display-name "OS version"
             :json-schema/description "operating system version"

             :json-schema/order 29)))


(s/def ::hw-revision-code
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "hw-revision-code"
             :json-schema/display-name "hardware revision code"
             :json-schema/description "hardware revision code"

             :json-schema/order 30)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb/attributes
                     {:req-un [::state
                               ::owner
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
                               ::hw-revision-code]}))

