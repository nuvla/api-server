(ns sixsq.nuvla.server.resources.spec.nuvlabox-status-0
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;;
;; general information
;;

(s/def ::next-heartbeat
  (-> (st/spec ::core/timestamp)
      (assoc :name "next-heartbeat"
             :json-schema/display-name "next heartbeat"
             :json-schema/description "timestamp for next heartbeat update"

             :json-schema/order 31)))


(s/def ::current-time
  (-> (st/spec ::core/timestamp)
      (assoc :name "current-time"
             :json-schema/display-name "current time"
             :json-schema/description "current time provided by the NuvlaBox clock"

             :json-schema/order 32)))


(s/def ::status
  (-> (st/spec #{"OPERATIONAL" "DEGRADED" "UNKNOWN"})
      (assoc :name "status"
             :json-schema/type "string"
             :json-schema/description "current status of the NuvlaBox"

             :json-schema/order 33)))


(s/def ::comment
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "comment"
             :json-schema/description "comment about the current NuvlaBox status"

             :json-schema/order 10)))


;;
;; resource information
;;

(s/def ::topic
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "topic"
             :json-schema/description "topic name within the NuvlaBox Data Gateway"

             :json-schema/order 36)))


(s/def ::raw-sample
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "raw sample"
             :json-schema/description "raw message sample published to the NB Data Gateway topic"

             :json-schema/order 37)))



(s/def ::capacity
  (-> (st/spec pos-int?)
      (assoc :name "capacity"
             :json-schema/type "integer"
             :json-schema/description "total capacity of the resource"

             :json-schema/order 11)))


(s/def ::load
  (-> (st/spec (s/and number? #(not (neg? %))))
      (assoc :name "load"
             :json-schema/type "double"
             :json-schema/description "CPU load"

             :json-schema/order 12)))


(s/def ::bytes-transmitted
  (-> (st/spec (s/and number? #(not (neg? %))))
      (assoc :name "bytes-transmitted"
             :json-schema/type "double"
             :json-schema/description "number of bytes transmitted tx_bytes"

             :json-schema/order 43)))


(s/def ::bytes-received
  (-> (st/spec (s/and number? #(not (neg? %))))
      (assoc :name "bytes-received"
             :json-schema/type "double"
             :json-schema/description "number of bytes received rx_bytes"

             :json-schema/order 43)))


(s/def ::cpu
  (-> (st/spec (su/only-keys :req-un [::capacity ::load] :opt-un [::topic ::raw-sample]))
      (assoc :name "cpu"
             :json-schema/type "map"
             :json-schema/display-name "CPU"
             :json-schema/description "CPU capacity and current load"

             :json-schema/order 22)))


(s/def ::used
  (-> (st/spec nat-int?)
      (assoc :name "used"
             :json-schema/type "integer"
             :json-schema/description "used quantity of the resource"

             :json-schema/order 12)))


(s/def ::ram
  (-> (st/spec (su/only-keys :req-un [::capacity ::used] :opt-un [::topic ::raw-sample]))
      (assoc :name "ram"
             :json-schema/type "map"
             :json-schema/description "available and consumed RAM"

             :json-schema/order 23)))


(s/def ::interface
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "interface"
             :json-schema/description "network interface name"

             :json-schema/order 44)))


(s/def ::net-interface-stat
  (-> (st/spec (su/only-keys :req-un [::interface ::bytes-received ::bytes-transmitted]))
      (assoc :name "net-interface-stat"
             :json-schema/type "map"
             :json-schema/description "txBytes and rxBytes for each network interface"

             :json-schema/order 45)))


(s/def ::net-stats
  (-> (st/spec (s/coll-of ::net-interface-stat :min-count 1 :kind vector?))
      (assoc :name "network-interfaces"
             :json-schema/type "array"
             :json-schema/description "txBytes and rxBytes for each network interface"

             :json-schema/order 46)))

(s/def ::bcm
  (-> (st/spec nat-int?)
      (assoc :name "bcm"
             :json-schema/description "BCM (Broadcom SOC channel) pin number"

             :json-schema/order 47)))

(s/def ::name
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "name"
             :json-schema/description "Name of the pin (or underlying function)"

             :json-schema/order 48)))

(s/def ::mode
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "mode"
             :json-schema/description "How the pin is being used. Usually is one of in/out/pwm/clock/up/down/tri/ALT#"

             :json-schema/order 49)))

(s/def ::voltage
  (-> (st/spec nat-int?)
      (assoc :name "voltage"
             :json-schema/description "Voltage level of the pin"

             :json-schema/order 50)))

(s/def ::pin
  (-> (st/spec pos-int?)
      (assoc :name "pin"
             :json-schema/description "Physical pin number"

             :json-schema/order 51)))

(s/def ::gpio-object
  (-> (st/spec (su/only-keys :req-un [::pin] :opt-un [::bcm ::name ::mode ::voltage]))
      (assoc :name "gpio-object"
             :json-schema/type "map"
             :json-schema/description "a GPIO pin and its inforatiom"

             :json-schema/order 52)))

(s/def ::gpio-pins
  (-> (st/spec (s/coll-of ::gpio-object :min-count 1 :kind vector?))
      (assoc :name "gpio-pins"
             :json-schema/type "array"
             :json-schema/description "list of GPIO pins and their information"

             :json-schema/order 53)))

(s/def ::nuvlabox-engine-version
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "nuvlabox-engine-version"
             :json-schema/description "nuvlabox engine release"

             :json-schema/order 54)))


(s/def ::device
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "device"
             :json-schema/description "name of disk device"

             :json-schema/order 10)))


(s/def ::disk-info
  (-> (st/spec (su/only-keys :req-un [::device ::capacity ::used] :opt-un [::topic ::raw-sample]))
      (assoc :name "disk-info"
             :json-schema/type "map"
             :json-schema/description "available and consumed disk space for device")))


(s/def ::disks
  (-> (st/spec (s/coll-of ::disk-info :min-count 1 :kind vector?))
      (assoc :name "disks"
             :json-schema/type "array"
             :json-schema/description "available and consumed disk space for devices"

             :json-schema/order 24)))


(s/def ::resources
  (-> (st/spec (su/only-keys :req-un [::cpu ::ram ::disks] :opt-un [::net-stats]))
      (assoc :name "resources"
             :json-schema/type "map"
             :json-schema/description "available and consumed resources"

             :json-schema/order 33)))


(s/def ::operating-system
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "operating-system"
             :json-schema/description "name of the host OS"

             :json-schema/order 37)))


(s/def ::architecture
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "architecture"
             :json-schema/description "platform hw architecture"

             :json-schema/order 38)))


(s/def ::hostname
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "hostname"
             :json-schema/description "device hostname"

             :json-schema/order 39)))


(s/def ::ip
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "ip"
             :json-schema/description "device IP, as used by the NuvlaBox"

             :json-schema/order 40)))


(s/def ::docker-server-version
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "docker server version"
             :json-schema/description "docker server version on the host"

             :json-schema/order 41)))


(s/def ::last-boot
  (-> (st/spec ::core/timestamp)
      (assoc :name "last boot"
             :json-schema/description "last boot time"

             :json-schema/order 42)))
;;
;; peripherals
;;

(s/def ::vendor-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "vendor-id"
             :json-schema/display-name "vendor ID"
             :json-schema/description "unique identifier of the peripheral vendor"

             :json-schema/order 11)))


(s/def ::device-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "device-id"
             :json-schema/display-name "device ID"
             :json-schema/description "unique identifier of the device"

             :json-schema/order 12)))


(s/def ::bus-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "bus-id"
             :json-schema/display-name "USB bus ID"
             :json-schema/description "unique identifier of the USB bus"

             :json-schema/order 13)))


(s/def ::product-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "product-id"
             :json-schema/display-name "product ID"
             :json-schema/description "unique identifier of the product"

             :json-schema/order 14)))


(s/def ::description
  (-> (st/spec string?)
      (assoc :name "description"
             :json-schema/type "string"
             :json-schema/description "description of the peripheral"

             :json-schema/order 14)))


(s/def ::usb-info
  (-> (st/spec (su/only-keys :req-un [::vendor-id
                                      ::device-id
                                      ::bus-id
                                      ::product-id
                                      ::description]))
      (assoc :name "usb-info"
             :json-schema/type "map"
             :json-schema/description "USB peripheral information")))


(s/def ::usb
  (-> (st/spec (s/coll-of ::usb-info :kind vector?))
      (assoc :name "usb"
             :json-schema/type "array"
             :json-schema/description "USB peripherals"

             :json-schema/order 20)))


(s/def ::peripherals
  (-> (st/spec (su/only-keys :opt-un [::usb]))
      (assoc :name "peripherals"
             :json-schema/type "map"
             :json-schema/description "state of visible peripherals"

             :json-schema/order 34)))

;;
;; miscellaneous
;;

(s/def ::wifi-password
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "wifi-password"
             :json-schema/description "WIFI password for the NuvlaBox"

             :json-schema/order 35)))


(s/def ::nuvlabox-api-endpoint
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "NuvlaBox API Endpoint"
             :json-schema/description "Full endpoint for the NuvlaBox API"

             :json-schema/order 36)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb-status/attributes
                     {:req-un [::status]
                      :opt-un [::next-heartbeat
                               ::current-time
                               ::comment
                               ::resources
                               ::operating-system
                               ::architecture
                               ::hostname
                               ::ip
                               ::docker-server-version
                               ::last-boot
                               ::peripherals
                               ::wifi-password
                               ::nuvlabox-api-endpoint
                               ::gpio-pins
                               ::nuvlabox-engine-version
                               ::gpio-pins]}))
