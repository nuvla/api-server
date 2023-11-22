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
  (assoc (st/spec ::core/timestamp)
    :name "next-heartbeat"
    :json-schema/display-name "next heartbeat"
    :json-schema/description "timestamp for next heartbeat update"
    :json-schema/order 31))

(s/def ::last-heartbeat
  (assoc (st/spec ::core/timestamp)
    :name "last-heartbeat"
    :json-schema/display-name "last heartbeat"
    :json-schema/description "timestamp for last heartbeat update"

    :json-schema/order 40))

(s/def ::next-telemetry
  (assoc (st/spec ::core/timestamp)
    :name "next-telemetryt"
    :json-schema/display-name "next telemetry"
    :json-schema/description "timestamp for next telemetry update"
    :json-schema/order 41))

(s/def ::last-telemetry
  (assoc (st/spec ::core/timestamp)
    :name "last-telemetry"
    :json-schema/display-name "last telemetry"
    :json-schema/description "timestamp for last telemetry update"

    :json-schema/order 42))

(s/def ::current-time
  (assoc (st/spec ::core/timestamp)
    :name "current-time"
    :json-schema/display-name "current time"
    :json-schema/description "current time provided by the NuvlaBox clock"
    :json-schema/order 32))

(s/def ::status
  (assoc (st/spec #{"OPERATIONAL" "DEGRADED" "UNKNOWN"})
    :name "status"
    :json-schema/type "string"
    :json-schema/description "current status of the NuvlaBox"
    :json-schema/value-scope {:values ["OPERATIONAL" "DEGRADED" "UNKNOWN"]}
    :json-schema/order 33))

(s/def ::comment
  (assoc (st/spec ::core/nonblank-string)
    :name "comment"
    :json-schema/description "comment about the current NuvlaBox status"
    :json-schema/order 10))

;;
;; resource information
;;

(s/def ::topic
  (assoc (st/spec ::core/nonblank-string)
    :name "topic"
    :json-schema/description "topic name within the NuvlaBox Data Gateway"
    :json-schema/order 36))

(s/def ::raw-sample
  (assoc (st/spec ::core/nonblank-string)
    :name "raw sample"
    :json-schema/description "raw message sample published to the NB Data Gateway topic"
    :json-schema/order 37))


(s/def ::capacity
  (assoc (st/spec pos-int?)
    :name "capacity"
    :json-schema/type "integer"
    :json-schema/description "total capacity of the resource"
    :json-schema/order 11))

(s/def ::load
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "load"
    :json-schema/type "double"
    :json-schema/description "CPU load"
    :json-schema/order 12))

(s/def ::load-1
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "load-1"
    :json-schema/type "double"
    :json-schema/description "CPU load last minute"))

(s/def ::load-5
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "load-5"
    :json-schema/type "double"
    :json-schema/description "CPU load last 5 minutes"))

(s/def ::bytes-transmitted
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "bytes-transmitted"
    :json-schema/type "double"
    :json-schema/description "number of bytes transmitted tx_bytes"
    :json-schema/order 43))

(s/def ::bytes-received
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "bytes-received"
    :json-schema/type "double"
    :json-schema/description "number of bytes received rx_bytes"
    :json-schema/order 43))

(s/def ::context-switches
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "context-switches"
    :json-schema/type "double"
    :json-schema/description "number of CPU context switches since boot"))

(s/def ::interrupts
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "interrupts"
    :json-schema/type "double"
    :json-schema/description "number of CPU interrupts since boot"))

(s/def ::software-interrupts
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "software-interrupts"
    :json-schema/type "double"
    :json-schema/description "number of CPU software interrupts since boot"))

(s/def ::system-calls
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "system-calls"
    :json-schema/type "double"
    :json-schema/description "number of CPU system calls since boot"))

(s/def ::cpu
  (assoc (st/spec (su/only-keys
                    :req-un [::capacity
                             ::load]
                    :opt-un [::topic
                             ::raw-sample
                             ::load-1
                             ::load-5
                             ::context-switches
                             ::interrupts
                             ::software-interrupts
                             ::system-calls]))
    :name "cpu"
    :json-schema/type "map"
    :json-schema/display-name "CPU"
    :json-schema/description "CPU capacity and current load"
    :json-schema/order 22))

(s/def ::used
  (assoc (st/spec nat-int?)
    :name "used"
    :json-schema/type "integer"
    :json-schema/description "used quantity of the resource"
    :json-schema/order 12))

(s/def ::ram
  (assoc (st/spec (su/only-keys
                    :req-un [::capacity
                             ::used]
                    :opt-un [::topic
                             ::raw-sample]))
    :name "ram"
    :json-schema/type "map"
    :json-schema/description "available and consumed RAM"
    :json-schema/order 23))

(s/def ::interface
  (assoc (st/spec ::core/nonblank-string)
    :name "interface"
    :json-schema/description "network interface name"
    :json-schema/order 44))

(s/def ::net-interface-stat
  (assoc (st/spec (su/only-keys
                    :req-un [::interface
                             ::bytes-received
                             ::bytes-transmitted]))
    :name "net-interface-stat"
    :json-schema/type "map"
    :json-schema/description "txBytes and rxBytes for each network interface"
    :json-schema/order 45))

(s/def ::net-stats
  (assoc (st/spec (s/coll-of ::net-interface-stat :min-count 1 :kind vector?))
    :name "network-interfaces"
    :json-schema/type "array"
    :json-schema/description "txBytes and rxBytes for each network interface"
    :json-schema/order 46))

(s/def ::bcm
  (assoc (st/spec nat-int?)
    :name "bcm"
    :json-schema/description "BCM (Broadcom SOC channel) pin number"
    :json-schema/order 47))

(s/def ::name
  (assoc (st/spec ::core/nonblank-string)
    :name "name"
    :json-schema/description "Name of the pin (or underlying function)"
    :json-schema/order 48))

(s/def ::mode
  (assoc (st/spec ::core/nonblank-string)
    :name "mode"
    :json-schema/description "How the pin is being used. Usually is one of in/out/pwm/clock/up/down/tri/ALT#"
    :json-schema/order 49))

(s/def ::voltage
  (assoc (st/spec nat-int?)
    :name "voltage"
    :json-schema/description "Voltage level of the pin"
    :json-schema/order 50))

(s/def ::pin
  (assoc (st/spec pos-int?)
    :name "pin"
    :json-schema/description "Physical pin number"
    :json-schema/order 51))

(s/def ::gpio-object
  (assoc (st/spec (su/only-keys
                    :req-un [::pin]
                    :opt-un [::bcm
                             ::name
                             ::mode
                             ::voltage]))
    :name "gpio-object"
    :json-schema/type "map"
    :json-schema/description "a GPIO pin and its inforatiom"
    :json-schema/order 52))

(s/def ::gpio-pins
  (assoc (st/spec (s/coll-of ::gpio-object :min-count 1 :kind vector?))
    :name "gpio-pins"
    :json-schema/type "array"
    :json-schema/description "list of GPIO pins and their information"
    :json-schema/indexed false
    :json-schema/order 53))

(s/def ::nuvlabox-engine-version
  (assoc (st/spec ::core/nonblank-string)
    :name "nuvlabox-engine-version"
    :json-schema/description "nuvlabox engine release"
    :json-schema/order 54))

(s/def ::device
  (assoc (st/spec ::core/nonblank-string)
    :name "device"
    :json-schema/description "name of disk device"
    :json-schema/order 10))

(s/def ::disk-info
  (assoc (st/spec (su/only-keys
                    :req-un [::device
                             ::capacity
                             ::used]
                    :opt-un [::topic
                             ::raw-sample]))
    :name "disk-info"
    :json-schema/type "map"
    :json-schema/description "available and consumed disk space for device"))

(s/def ::disks
  (assoc (st/spec (s/coll-of ::disk-info :min-count 1 :kind vector?))
    :name "disks"
    :json-schema/type "array"
    :json-schema/description "available and consumed disk space for devices"
    :json-schema/order 24))

(s/def ::metric-name
  (assoc (st/spec ::core/nonblank-string)
    :name "metric-name"
    :json-schema/description "name of the metric"
    :json-schema/order 67))

(s/def ::energy-consumption
  (assoc (st/spec number?)
    :name "energy-consumption"
    :json-schema/description "value of energy consumption for the metric"
    :json-schema/order 68))

(s/def ::unit
  (assoc (st/spec ::core/nonblank-string)
    :name "unit"
    :json-schema/description "metric value units"
    :json-schema/order 69))

(s/def ::power-consumption-metric
  (assoc (st/spec (su/only-keys
                    :req-un [::metric-name
                             ::energy-consumption
                             ::unit]))
    :name "power-consumption-metric"
    :json-schema/type "map"
    :json-schema/description "{metric-name energy-consumption unit} for a specifc power consumption metric"
    :json-schema/order 70))

(s/def ::power-consumption
  (assoc (st/spec (s/coll-of
                    ::power-consumption-metric
                    :kind vector?))
    :name "power-consumption"
    :json-schema/type "array"
    :json-schema/description "list of power-consumption-metric resources"
    :json-schema/order 71))

(s/def ::id
  (assoc (st/spec ::core/nonblank-string)
    :name "id"
    :json-schema/description "Container ID"
    :json-schema/order 86))

(s/def ::name
  (assoc (st/spec ::core/nonblank-string)
    :name "name"
    :json-schema/description "Container name"
    :json-schema/order 87))

(s/def ::container-status
  (assoc (st/spec ::core/nonblank-string)
    :name "status"
    :json-schema/description "Container status"
    :json-schema/order 88))

(s/def ::cpu-percent
  (assoc (st/spec ::core/nonblank-string)
    :name "cpu"
    :json-schema/description "Container CPU usage (%)"
    :json-schema/order 89))

(s/def ::mem-usage-limit
  (assoc (st/spec ::core/nonblank-string)
    :name "mem-usage-limit"
    :json-schema/description "Container memory usage and limit"
    :json-schema/order 90))

(s/def ::mem-percent
  (assoc (st/spec ::core/nonblank-string)
    :name "mem"
    :json-schema/description "Container memory usage (%)"
    :json-schema/order 91))

(s/def ::net-in-out
  (assoc (st/spec ::core/nonblank-string)
    :name "net-in-out"
    :json-schema/description "Container network usage, in and out"
    :json-schema/order 92))

(s/def ::blk-in-out
  (assoc (st/spec ::core/nonblank-string)
    :name "blk-in-out"
    :json-schema/description "Container block devices usage, in and out"
    :json-schema/order 93))

(s/def ::restart-count
  (assoc (st/spec nat-int?)
    :name "restart-count"
    :json-schema/description "Container restart count"
    :json-schema/order 94))

(s/def ::cstat
  (assoc (st/spec (su/only-keys
                    :req-un [::id
                             ::name]
                    :opt-un [::container-status
                             ::restart-count
                             ::cpu-percent
                             ::mem-percent
                             ::mem-usage-limit
                             ::net-in-out
                             ::blk-in-out]))
    :name "cstat"
    :json-schema/type "map"
    :json-schema/display-name "Single Container Stats"
    :json-schema/description "Single Container monitoring statistics"
    :json-schema/order 95))

(s/def ::container-stats
  (assoc (st/spec (s/coll-of ::cstat :kind vector?))
    :name "container-stats"
    :json-schema/type "array"
    :json-schema/description "Container monitoring stats, per container inside the NuvlaBox"
    :json-schema/order 96))

(s/def ::resources
  (assoc (st/spec (su/only-keys
                    :req-un [::cpu
                             ::ram
                             ::disks]
                    :opt-un [::net-stats
                             ::power-consumption
                             ::container-stats]))
    :name "resources"
    :json-schema/type "map"
    :json-schema/description "available and consumed resources"
    :json-schema/indexed false
    :json-schema/order 33))

(s/def ::resources-prev
  (assoc (st/spec (s/nilable (su/only-keys
                               :req-un [::cpu
                                        ::ram
                                        ::disks]
                               :opt-un [::net-stats
                                        ::power-consumption
                                        ::container-stats])))
    :name "resources-prev"
    :json-schema/type "map"
    :json-schema/description "available and consumed resources; previous values."
    :json-schema/indexed false
    :json-schema/order 30))

(s/def ::operating-system
  (assoc (st/spec ::core/nonblank-string)
    :name "operating-system"
    :json-schema/description "name of the host OS"
    :json-schema/order 37))

(s/def ::architecture
  (assoc (st/spec ::core/nonblank-string)
    :name "architecture"
    :json-schema/description "platform hw architecture"
    :json-schema/order 38))

(s/def ::hostname
  (assoc (st/spec ::core/nonblank-string)
    :name "hostname"
    :json-schema/description "device hostname"
    :json-schema/order 39))

(s/def ::ip
  (assoc (st/spec ::core/nonblank-string)
    :name "ip"
    :json-schema/description "device IP, as used by the NuvlaBox"
    :json-schema/order 40))

(s/def ::docker-server-version
  (assoc (st/spec ::core/nonblank-string)
    :name "docker server version"
    :json-schema/description "docker server version on the host"
    :json-schema/order 41))

(s/def ::last-boot
  (assoc (st/spec ::core/timestamp)
    :name "last boot"
    :json-schema/description "last boot time"
    :json-schema/order 42))
;;
;; peripherals
;;

(s/def ::vendor-id
  (assoc (st/spec ::core/nonblank-string)
    :name "vendor-id"
    :json-schema/display-name "vendor ID"
    :json-schema/description "unique identifier of the peripheral vendor"
    :json-schema/order 11))

(s/def ::device-id
  (assoc (st/spec ::core/nonblank-string)
    :name "device-id"
    :json-schema/display-name "device ID"
    :json-schema/description "unique identifier of the device"
    :json-schema/order 12))

(s/def ::bus-id
  (assoc (st/spec ::core/nonblank-string)
    :name "bus-id"
    :json-schema/display-name "USB bus ID"
    :json-schema/description "unique identifier of the USB bus"
    :json-schema/order 13))

(s/def ::product-id
  (assoc (st/spec ::core/nonblank-string)
    :name "product-id"
    :json-schema/display-name "product ID"
    :json-schema/description "unique identifier of the product"
    :json-schema/order 14))

(s/def ::description
  (assoc (st/spec string?)
    :name "description"
    :json-schema/type "string"
    :json-schema/description "description of the peripheral"
    :json-schema/order 14))

(s/def ::usb-info
  (assoc (st/spec (su/only-keys
                    :req-un [::vendor-id
                             ::device-id
                             ::bus-id
                             ::product-id
                             ::description]))
    :name "usb-info"
    :json-schema/type "map"
    :json-schema/description "USB peripheral information"))

(s/def ::usb
  (assoc (st/spec (s/coll-of ::usb-info :kind vector?))
    :name "usb"
    :json-schema/type "array"
    :json-schema/description "USB peripherals"
    :json-schema/order 20))

(s/def ::peripherals
  (assoc (st/spec (su/only-keys :opt-un [::usb]))
    :name "peripherals"
    :json-schema/type "map"
    :json-schema/description "state of visible peripherals"
    :json-schema/order 34))

;;
;; miscellaneous
;;

(s/def ::wifi-password
  (assoc (st/spec ::core/nonblank-string)
    :name "wifi-password"
    :json-schema/description "WIFI password for the NuvlaBox"
    :json-schema/indexed false
    :json-schema/order 35))

(s/def ::nuvlabox-api-endpoint
  (assoc (st/spec ::core/nonblank-string)
    :name "NuvlaBox API Endpoint"
    :json-schema/description "Full endpoint for the NuvlaBox API"
    :json-schema/order 36))

;;
;; self inferred location
;;

(s/def ::inferred-location
  (assoc (st/spec (s/coll-of number? :min-count 2 :max-count 3))
    :name "inferred-location"
    :json-schema/type "geo-point"
    :json-schema/display-name "inferred-location"
    :json-schema/description "location [longitude, latitude, altitude] - dynamically inferred by the NuvlaBox"
    :json-schema/order 56))

(s/def ::docker-plugins
  (assoc (st/spec (s/coll-of ::core/nonblank-string :kind vector?))
    :name "Docker Plugins"
    :json-schema/description "List of enabled Docker Plugins on the NuvlaBox host"
    :json-schema/order 55))

;;
;; vulnerabilities
;;

(s/def ::vulnerability-id
  (assoc (st/spec ::core/nonblank-string)
    :name "vulnerability-id"
    :json-schema/description "unique ID for the vulnerability"
    :json-schema/order 57))

(s/def ::vulnerability-description
  (assoc (st/spec ::core/nonblank-string)
    :name "vulnerability-description"
    :json-schema/description "Detailed description of the vulnerability"
    :json-schema/order 58))

(s/def ::product
  (assoc (st/spec ::core/nonblank-string)
    :name "product"
    :json-schema/description "Specific product name corresponding to the vulnerability"
    :json-schema/order 60))

(s/def ::vulnerability-reference
  (assoc (st/spec ::core/nonblank-string)
    :name "vulnerability-reference"
    :json-schema/description "Link for online database with vulnerability info"
    :json-schema/order 61))

(s/def ::vulnerability-score
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "vulnerability-score"
    :json-schema/description "CVSS score for the vulnerability"
    :json-schema/order 62))

(s/def ::vulnerability-info
  (assoc (st/spec (su/only-keys
                    :req-un [::vulnerability-id
                             ::product]
                    :opt-un [::vulnerability-description
                             ::vulnerability-reference
                             ::vulnerability-score]))
    :name "vulnerability-info"
    :json-schema/type "map"
    :json-schema/description "complete vulnerability entry"))

(s/def ::items
  (assoc (st/spec (s/coll-of ::vulnerability-info :kind vector?))
    :name "vulnerabilities"
    :json-schema/type "array"
    :json-schema/description "list of vulnerabilities affecting the NuvlaBox"
    :json-schema/order 59))

(s/def ::average-score
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "average-score"
    :json-schema/description "Average CVSS score for all vulnerabilities"
    :json-schema/order 63))

(s/def ::total
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "total"
    :json-schema/description "Total count of vulnerabilities found"
    :json-schema/order 64))

(s/def ::affected-products
  (assoc (st/spec (s/coll-of ::core/nonblank-string :kind vector?))
    :name "affected-products"
    :json-schema/description "List of affected products in the detected vulnerabilities"
    :json-schema/order 65))

(s/def ::summary
  (assoc (st/spec (su/only-keys
                    :req-un [::total
                             ::affected-products]
                    :opt-un [::average-score]))
    :name "summary"
    :json-schema/type "map"
    :json-schema/description "Summary of the vulnerability scan"))

(s/def ::vulnerabilities
  (assoc (st/spec (su/only-keys
                    :opt-un [::summary
                             ::items]))
    :name "vulnerabilities"
    :json-schema/type "map"
    :json-schema/description "list of vulnerabilities affecting the NuvlaBox, plus summary"
    :json-schema/indexed false
    :json-schema/order 66))

(s/def ::swarm-node-id
  (assoc (st/spec ::core/nonblank-string)
    :name "swarm-node-id"
    :json-schema/description "ID of the underlying Swarm node"
    :json-schema/order 67))

(s/def ::project-name
  (assoc (st/spec ::core/nonblank-string)
    :name "project-name"
    :json-schema/description "Name of the project used during the NuvlaBox Engine installation"
    :json-schema/order 68))

(s/def ::config-files
  (assoc (st/spec (s/coll-of ::core/nonblank-string :min-count 1 :kind vector?))
    :name "config-files"
    :json-schema/description "List of files (compose files or manifests) used during the NuvlaBox Engine installation"
    :json-schema/order 69))

(s/def ::working-dir
  (assoc (st/spec ::core/nonblank-string)
    :name "working-dir"
    :json-schema/description "Directory on the host, from where the NuvlaBox Engine was installed"
    :json-schema/order 70))

(s/def ::environment
  (assoc (st/spec (s/coll-of ::core/nonblank-string :kind vector?))
    :name "environment"
    :json-schema/description "List of environment variables set at installation time"
    :json-schema/order 71))

(s/def ::installation-parameters
  (assoc (st/spec (su/only-keys
                    :opt-un [::working-dir
                             ::config-files
                             ::project-name
                             ::environment]))
    :name "installation-parameters"
    :json-schema/type "map"
    :json-schema/description "Parameters and configurations used for the NuvlaBox Engine installation"
    :json-schema/order 72))

(def job-regex #"^job/[a-z0-9]+(-[a-z0-9]+)*$")

(s/def ::job-id (-> (st/spec (s/and string? #(re-matches job-regex %)))))

(s/def ::jobs
  (assoc (st/spec (s/coll-of ::job-id :kind vector?))
    :name "jobs"
    :json-schema/description "List of jobs to be executed by NuvlaBox"
    :json-schema/indexed false
    :json-schema/order 73))

(s/def ::swarm-node-cert-expiry-date
  (assoc (st/spec ::core/timestamp)
    :name "swarm-node-cert-expiry-date"
    :json-schema/description "Expiry date of the Docker Swarm CA certificates"
    :json-schema/order 74))

(s/def ::online
  (assoc (st/spec boolean?)
    :name "online"
    :json-schema/type "boolean"
    :json-schema/description "Indicate if the nuvlabox is connected to Nuvla service based on next-heartbeat attribute"
    :json-schema/server-managed true
    :json-schema/order 75))

(s/def ::online-prev
  (assoc (st/spec boolean?)
    :name "online-prev"
    :json-schema/type "boolean"
    :json-schema/description "Indicates previous value of
::online"
    :json-schema/server-managed true
    :json-schema/order 76))

(s/def ::thermal-zone
  (assoc (st/spec ::core/nonblank-string)
    :name "thermal-zone"
    :json-schema/description "Name of the thermal zone"
    :json-schema/order 77))

(s/def ::value
  (assoc (st/spec number?)
    :name "value"
    :json-schema/description "Temperature of the thermal zone"
    :json-schema/order 78))

(s/def ::temperature-metric
  (assoc (st/spec (su/only-keys
                    :req-un [::thermal-zone
                             ::value]))
    :name "temperature-metric"
    :json-schema/type "map"
    :json-schema/description "{thermal-zone value} combination"
    :json-schema/order 79))

(s/def ::temperatures
  (assoc (st/spec (s/coll-of ::temperature-metric :kind vector?))
    :name "temperatures"
    :json-schema/type "array"
    :json-schema/description "list of temperatures in the edge device"
    :json-schema/indexed false
    :json-schema/order 80))

(s/def ::kubelet-version
  (assoc (st/spec ::core/nonblank-string)
    :name "kubelet version"
    :json-schema/description "kubelet version on the host"
    :json-schema/order 81))

(s/def ::container-plugins
  (assoc (st/spec (s/coll-of ::core/nonblank-string :kind vector?))
    :name "Container Plugins"
    :json-schema/description "List of enabled Container Plugins on the NuvlaBox host"
    :json-schema/order 82))

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb-status/attributes
                     {:req-un [::status]
                      :opt-un [::next-heartbeat
                               ::last-heartbeat
                               ::next-telemetry
                               ::last-telemetry
                               ::current-time
                               ::comment
                               ::resources
                               ::resources-prev
                               ::operating-system
                               ::architecture
                               ::hostname
                               ::ip
                               ::docker-server-version
                               ::last-boot
                               ::peripherals
                               ::wifi-password
                               ::nuvlabox-api-endpoint
                               ::inferred-location
                               ::gpio-pins
                               ::nuvlabox-engine-version
                               ::gpio-pins
                               ::docker-plugins
                               ::vulnerabilities
                               ::swarm-node-id
                               ::installation-parameters
                               ::jobs
                               ::swarm-node-cert-expiry-date
                               ::online
                               ::online-prev
                               ::temperatures
                               ::container-plugins]}))
